package com.armilla.neckcare.scene.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain vector so geometry can be generated and unit-tested without the SDK. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun length() = sqrt(dot(this))

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-9f) Vec3(0f, 1f, 0f) else this * (1f / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}

/** Triangle soup that can be appended to, so many small parts become one draw call. */
class MeshData {
    val positions = ArrayList<Vec3>()
    val indices = ArrayList<Int>()

    val isEmpty: Boolean
        get() = indices.isEmpty()

    fun append(other: MeshData): MeshData {
        val base = positions.size
        positions.addAll(other.positions)
        other.indices.forEach { indices.add(it + base) }
        return this
    }

    /** Tube of [radius] along [path]. Open tubes get rounded ends, as the design's line caps. */
    fun tube(path: List<Vec3>, radius: Float, sides: Int = 8, closed: Boolean = false): MeshData {
        if (path.size < 2) return this
        val base = positions.size
        val count = path.size
        // Parallel-transported frame: no twisting along arcs, and stable on straight runs.
        var tangent = (path[1] - path[0]).normalized()
        var normal = perpendicular(tangent)
        for (i in 0 until count) {
            val next = if (i < count - 1) path[i + 1] else if (closed) path[0] else path[i]
            val prev = if (i > 0) path[i - 1] else if (closed) path[count - 1] else path[i]
            val t = (next - prev).normalized()
            val axis = tangent.cross(t)
            if (axis.length() > 1e-6f) {
                val angle = kotlin.math.asin(axis.length().coerceIn(-1f, 1f))
                normal = rotate(normal, axis.normalized(), angle)
            }
            tangent = t
            normal = (normal - tangent * normal.dot(tangent)).normalized()
            val binormal = tangent.cross(normal)
            for (s in 0 until sides) {
                val a = 2.0 * PI * s / sides
                positions += path[i] + normal * (radius * cos(a).toFloat()) + binormal * (radius * sin(a).toFloat())
            }
        }
        val rings = if (closed) count else count - 1
        for (i in 0 until rings) {
            val r0 = base + i * sides
            val r1 = base + ((i + 1) % count) * sides
            for (s in 0 until sides) {
                val s1 = (s + 1) % sides
                indices += listOf(r0 + s, r1 + s, r0 + s1, r0 + s1, r1 + s, r1 + s1)
            }
        }
        if (!closed) {
            sphere(path.first(), radius, 6, 4)
            sphere(path.last(), radius, 6, 4)
        }
        return this
    }

    fun sphere(center: Vec3, radius: Float, columns: Int = 16, rows: Int = 10): MeshData {
        val base = positions.size
        for (r in 0..rows) {
            val lat = PI * r / rows - PI / 2
            for (c in 0..columns) {
                val lon = 2.0 * PI * c / columns
                positions +=
                    center +
                        Vec3(
                            (radius * cos(lat) * cos(lon)).toFloat(),
                            (radius * sin(lat)).toFloat(),
                            (radius * cos(lat) * sin(lon)).toFloat(),
                        )
            }
        }
        val stride = columns + 1
        for (r in 0 until rows) for (c in 0 until columns) {
            val a = base + r * stride + c
            indices += listOf(a, a + stride, a + 1, a + 1, a + stride, a + stride + 1)
        }
        return this
    }

    /** Flat disc facing [normal]; used for the dais and halo cards. */
    fun disc(center: Vec3, normal: Vec3, radius: Float, segments: Int = 48, innerRadius: Float = 0f): MeshData {
        val n = normal.normalized()
        val u = perpendicular(n)
        val v = n.cross(u)
        val base = positions.size
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            val dir = u * cos(a).toFloat() + v * sin(a).toFloat()
            positions += center + dir * innerRadius
            positions += center + dir * radius
        }
        for (i in 0 until segments) {
            val a = base + i * 2
            indices += listOf(a, a + 1, a + 2, a + 2, a + 1, a + 3)
        }
        return this
    }

    companion object {
        /**
         * Points of a circular arc: centre [center], radius [radius], in the plane spanned by the
         * unit vectors [zeroDir] (where the angle is 0) and [positiveDir] (toward +90°).
         */
        fun arc(
            center: Vec3,
            radius: Float,
            zeroDir: Vec3,
            positiveDir: Vec3,
            fromDeg: Float,
            toDeg: Float,
            stepDeg: Float = 3f,
        ): List<Vec3> {
            val span = toDeg - fromDeg
            val steps = maxOf(1, kotlin.math.ceil(abs(span) / stepDeg).toInt())
            return (0..steps).map { i ->
                val a = Math.toRadians((fromDeg + span * i / steps).toDouble())
                center + zeroDir * (radius * cos(a).toFloat()) + positiveDir * (radius * sin(a).toFloat())
            }
        }

        fun perpendicular(v: Vec3): Vec3 {
            val other = if (abs(v.y) < 0.9f) Vec3.Y else Vec3.X
            return v.cross(other).normalized()
        }

        /** Rodrigues rotation of [v] about unit [axis] by [angleRad]. */
        fun rotate(v: Vec3, axis: Vec3, angleRad: Float): Vec3 {
            val c = cos(angleRad)
            val s = sin(angleRad)
            return v * c + axis.cross(v) * s + axis * (axis.dot(v) * (1 - c))
        }
    }
}

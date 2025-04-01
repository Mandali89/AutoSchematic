package ir.wylot.autoschematic.compatibility

import org.bukkit.Bukkit

object VersionHandler {
    private val serverVersion: ServerVersion by lazy { detectServerVersion() }
    private val serverSoftware: ServerSoftware by lazy { detectServerSoftware() }

    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    data class ServerVersion(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<ServerVersion> {
        override fun compareTo(other: ServerVersion): Int {
            return when {
                major != other.major -> major.compareTo(other.major)
                minor != other.minor -> minor.compareTo(other.minor)
                else -> patch.compareTo(other.patch)
            }
        }

        fun isAtLeast(major: Int, minor: Int = 0, patch: Int = 0): Boolean {
            return this >= ServerVersion(major, minor, patch)
        }

        fun isBetween(min: ServerVersion, max: ServerVersion): Boolean {
            return this >= min && this <= max
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    enum class ServerSoftware {
        SPIGOT,
        PAPER,
        PURPUR,
        FOLIA,
        UNKNOWN;

        val isFork: Boolean get() = this != SPIGOT
    }

    private fun detectServerVersion(): ServerVersion {
        val versionString = Bukkit.getServer().bukkitVersion.split('-')[0]
        val parts = versionString.split('.').map { it.toIntOrNull() ?: 0 }

        return ServerVersion(
            major = parts.getOrElse(0) { 1 },
            minor = parts.getOrElse(1) { 0 },
            patch = parts.getOrElse(2) { 0 }
        )
    }

    private fun detectServerSoftware(): ServerSoftware {
        return when {
            isFolia -> ServerSoftware.FOLIA
            isPaper -> ServerSoftware.PAPER
            isPurpur -> ServerSoftware.PURPUR
            else -> ServerSoftware.SPIGOT
        }
    }

    private val isPaper: Boolean by lazy {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private val isPurpur: Boolean by lazy {
        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun getVersion(): ServerVersion = serverVersion

    fun getSoftware(): ServerSoftware = serverSoftware


    fun isAtLeast(major: Int, minor: Int = 0, patch: Int = 0): Boolean {
        return serverVersion.isAtLeast(major, minor, patch)
    }


    fun isBetween(
        minMajor: Int, minMinor: Int = 0, minPatch: Int = 0,
        maxMajor: Int, maxMinor: Int = 0, maxPatch: Int = 0
    ): Boolean {
        return serverVersion.isBetween(
            ServerVersion(minMajor, minMinor, minPatch),
            ServerVersion(maxMajor, maxMinor, maxPatch)
        )
    }

    fun getNMSVersion(): String {
        val packageName = Bukkit.getServer().javaClass.`package`.name
        return packageName.substring(packageName.lastIndexOf('.') + 1)
    }

    fun getNMSClass(className: String): Class<*> {
        return try {
            val path = if (isAtLeast(1, 17)) {
                "net.minecraft.$className"
            } else {
                "net.minecraft.server.${getNMSVersion()}.$className"
            }
            Class.forName(path)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Failed to load NMS class $className", e)
        }
    }

    fun getOBCClass(className: String): Class<*> {
        return try {
            Class.forName("org.bukkit.craftbukkit.${getNMSVersion()}.$className")
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Failed to load OBC class $className", e)
        }
    }
}
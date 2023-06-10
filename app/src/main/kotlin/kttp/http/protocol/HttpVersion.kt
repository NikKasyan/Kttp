package kttp.http.protocol


private const val HTTP_START = "HTTP/"

private val HTTP_VERSION_REGEX = Regex("^\\d\\.\\d")

class HttpVersion {

    companion object {
        val DEFAULT_VERSION = HttpVersion(1, 1)
    }

    val majorVersion: Int
    val minorVersion: Int

    constructor(httpVersionString: String) {
        if (!httpVersionString.startsWith(HTTP_START))
            throw InvalidHttpVersion("""HTTP Version has to start with "$HTTP_START"""")
        val version = httpVersionString.replaceFirst(HTTP_START, "")
        if (!version.matches(HTTP_VERSION_REGEX))
            throw InvalidHttpVersion("HTTP Version is invalid")

        val (majorVersionString, minorVersionString) = version.split(".")
        majorVersion = majorVersionString.toInt()
        minorVersion = minorVersionString.toInt()
    }

    constructor(majorVersion: Int, minorVersion: Int) {
        this.majorVersion = majorVersion
        this.minorVersion = minorVersion
    }

    override fun toString(): String {
        return "$HTTP_START$majorVersion.$minorVersion"
    }

}

class InvalidHttpVersion(msg: String) : InvalidHttpRequest(msg)



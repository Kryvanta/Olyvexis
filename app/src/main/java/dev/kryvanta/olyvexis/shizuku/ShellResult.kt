package dev.kryvanta.olyvexis.shizuku

import android.os.Parcel
import android.os.Parcelable

/** Result of a shell command returned from ShellService to the app over Binder. */
class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : Parcelable {
    val isSuccess get() = exitCode == 0

    override fun toString() = "ShellResult(exitCode=$exitCode, stdout=$stdout, stderr=$stderr)"

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
    }

    companion object {
        const val EXIT_TIMEOUT = -1
        const val EXIT_ERROR = -2

        @JvmField
        val CREATOR = object : Parcelable.Creator<ShellResult> {
            override fun createFromParcel(source: Parcel) =
                ShellResult(source.readInt(), source.readString().orEmpty(), source.readString().orEmpty())

            override fun newArray(size: Int) = arrayOfNulls<ShellResult>(size)
        }
    }
}

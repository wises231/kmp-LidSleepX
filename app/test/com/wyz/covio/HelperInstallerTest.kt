package com.wyz.covio.app

import com.wyz.covio.app.helper.HelperInstaller
import com.wyz.covio.app.helper.HelperProtocol
import kotlin.test.Test
import kotlin.test.assertTrue

class HelperInstallerTest {
    private val script = HelperInstaller.installScript("/Applications/Covio.app", uid = "501")

    @Test
    fun `install script locks the helper directory to root`() {
        assertTrue(script.contains("chown root:wheel '${HelperProtocol.HELPER_DIRECTORY}'"))
        assertTrue(script.contains("chmod 0755 '${HelperProtocol.HELPER_DIRECTORY}'"))
    }

    @Test
    fun `install script makes allowed uid read only for the user`() {
        assertTrue(
            script.contains(
                "chmod 0644 '${HelperProtocol.ALLOWED_UID_PATH}' '${HelperProtocol.VERSION_PATH}' '${HelperProtocol.APP_PATH_FILE}'",
            ),
        )
        assertTrue(script.contains("printf '%s\\n' '501' > '${HelperProtocol.ALLOWED_UID_PATH}'"))
    }

    @Test
    fun `install script writes the launch daemon plist`() {
        assertTrue(script.contains(HelperProtocol.PLIST_PATH))
        assertTrue(script.contains("chown root:wheel '${HelperProtocol.PLIST_PATH}'"))
        assertTrue(script.contains("chmod 0644 '${HelperProtocol.PLIST_PATH}'"))
        assertTrue(script.contains("--helper"))
    }
}

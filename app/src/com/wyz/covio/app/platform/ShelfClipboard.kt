package com.wyz.covio.app.platform

import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO

object ShelfClipboard {
    fun copy(path: Path): Boolean = runCatching {
        val file = path.toFile()
        val image = runCatching { ImageIO.read(file) }.getOrNull()
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> =
                if (image == null) {
                    arrayOf(DataFlavor.javaFileListFlavor)
                } else {
                    arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.imageFlavor)
                }

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                flavor == DataFlavor.javaFileListFlavor || (image != null && flavor == DataFlavor.imageFlavor)

            override fun getTransferData(flavor: DataFlavor): Any = when {
                flavor == DataFlavor.javaFileListFlavor -> listOf(file as File)
                image != null && flavor == DataFlavor.imageFlavor -> image as Image
                else -> throw UnsupportedFlavorException(flavor)
            }
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
        true
    }.getOrDefault(false)
}

/** Center-crops an image file to a square JPEG so avatars stay small (a few KB) whatever the original size. */
export async function resizeToSquare(file: File, size = 256): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  try {
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Canvas unavailable')
    const side = Math.min(bitmap.width, bitmap.height)
    ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size)
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Encoding failed'))), 'image/jpeg', 0.9),
    )
  } finally {
    bitmap.close()
  }
}

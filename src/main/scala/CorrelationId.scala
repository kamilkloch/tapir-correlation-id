object CorrelationId {
  val headerName: String = "x-correlation-id"

  private[this] val random = new java.util.Random()

  /** Returns new correlation ID */
  @annotation.nowarn def generate: String = {
    val n = Math.abs(random.nextLong())
    val buf = new Array[Byte](11)
    buf(0) = ((n % 26).toInt + 65).toByte
    buf(1) = (((n >> 5) % 26).toInt + 65).toByte
    buf(2) = (((n >> 10) % 26).toInt + 65).toByte
    buf(3) = '-'
    buf(4) = (((n >> 15) % 26).toInt + 65).toByte
    buf(5) = (((n >> 20) % 26).toInt + 65).toByte
    buf(6) = (((n >> 25) % 26).toInt + 65).toByte
    buf(7) = '-'
    buf(8) = (((n >> 30) % 26).toInt + 65).toByte
    buf(9) = (((n >> 35) % 26).toInt + 65).toByte
    buf(10) = (((n >> 40) % 26).toInt + 65).toByte
    new String(buf, 0, 0, buf.length)
  }
}

case class CorrelationId(value: Option[String])

package lis

import chisel3.util.log2Up


object LISTesterUtils {
    
  /**
  * Convert int data to binary string
  */
  def asNdigitBinary (source: Int, digits: Int): String = {
    val lstring = source.toBinaryString
    //val sign = if (source > 0) "%0" else "%1"
    if (source >= 0) {
      //val l: java.lang.Long = lstring.toLong
      val l: java.lang.Long = lstring.toLong
      String.format ("%0" + digits + "d", l)
    }
    else
      lstring.takeRight(digits)
  }
  
  /**
  * Format inData so that it is compatible with 32 AXI4Stream data
  */
  def formAXI4StreamRealData(inData: Seq[Int], dataWidth: Int): Seq[Int] = {
    inData.map(data => java.lang.Long.parseLong(
                                  asNdigitBinary(data, dataWidth) ++ 
                                  asNdigitBinary(0, dataWidth), 2).toInt)
  }
  
  /**
  * Check error
  */
  def checkError(expected: Seq[Int], received: Seq[Int], tolerance: Int = 1) {
    expected.zip(received).foreach {
      case (in, out) => {
        require(math.abs(in - out) <= tolerance, "Tolerance is not satisfied")
      }
    }
  }
}

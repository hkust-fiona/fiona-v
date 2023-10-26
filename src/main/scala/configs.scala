package fiona

import freechips.rocketchip._
import freechips.rocketchip.system._
import org.chipsalliance.cde.config._
import fiona._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._

trait HasFIONAParameters {
    val FIONAVLenMax = 32
    val FIONAXLen = 16
    val FIONANLane = 2
}

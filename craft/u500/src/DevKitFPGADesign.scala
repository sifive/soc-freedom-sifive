// See LICENSE for license details.

package sifive.freedom.unleashed

import Chisel._
import chisel3.experimental.{withClockAndReset}

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.system._
import freechips.rocketchip.util.{ElaborationArtefacts,ResetCatchAndSync}

import sifive.blocks.devices.msi._
import sifive.blocks.devices.chiplink._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pinctrl.{BasePin}

import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

object PinGen {
  def apply(): BasePin = {
    new BasePin()
  }
}

class DevKitWrapper()(implicit p: Parameters) extends LazyModule
{
  val sysClock  = p(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node
  val corePLL   = p(PLLFactoryKey)()
  val coreGroup = ClockGroup()
  val wrangler  = LazyModule(new ResetWrangler)
  val coreClock = ClockSinkNode(freqMHz = p(DevKitFPGAFrequencyKey))
  coreClock := wrangler.node := coreGroup := corePLL := sysClock

  // removing the debug trait is invasive, so we hook it up externally for now
  val jt = p(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  val topMod = LazyModule(new DevKitFPGADesign(wrangler.node, corePLL)(p))

  override lazy val module = new LazyRawModuleImp(this) {
    val (core, _) = coreClock.in(0)
    childClock := core.clock

    val djtag = topMod.module.debug.get.systemjtag.get
    djtag.jtag.TCK := jt.TCK
    djtag.jtag.TMS := jt.TMS
    djtag.jtag.TDI := jt.TDI
    jt.TDO := djtag.jtag.TDO

    djtag.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
    djtag.reset  := core.reset

    childReset := core.reset | topMod.module.debug.get.ndreset
  }
}

case object DevKitFPGAFrequencyKey extends Field[Double](100.0)

class DevKitFPGADesign(wranglerNode: ClockAdapterNode, corePLL: PLLNode)(implicit p: Parameters) extends RocketSubsystem
    with HasPeripheryDebug
{
  // hook up UARTs, based on configuration and available overlays
  p(PeripheryUARTKey).zip(p(UARTOverlayKey)).foreach { case (params, overlay) =>
    val controller = UARTAttachParams(params).attachTo(this)
    overlay.place(UARTDesignInput(controller.ioNode))
  }

  p(PeripherySPIKey).zip(p(SDIOOverlayKey)).foreach { case (params, overlay) =>
    val controller = SPIAttachParams(params).attachTo(this)
    overlay.place(SDIODesignInput(params, controller.ioNode))

    // Assuming MMC slot attached to SPIs. See TODO above.
    val mmc = new MMCDevice(controller.device)
    ResourceBinding {
      Resource(mmc, "reg").bind(ResourceAddress(0))
    }
  }

  // TODO: currently, only hook up one memory channel
  val ddr = p(DDROverlayKey).headOption.map(_.place(DDRDesignInput(p(ExtMem).get.master.base, wranglerNode, corePLL)))
  ddr.foreach { _.overlayOutput.ddr := TLFragmenter(64,128,holdFirstDeny=true) := mbus.toDRAMController(Some("xilinxvc707mig"))() }

  // Work-around for a kernel bug (command-line ignored if /chosen missing)
  val chosen = new DeviceSnippet {
    def describe() = Description("chosen", Map())
  }

  // hook the first PCIe the board has
  val pcies = p(PCIeOverlayKey).headOption.map(_.place(PCIeDesignInput(wranglerNode, corePLL = corePLL)).overlayOutput)
  pcies.zipWithIndex.map { case(oo, i) =>
    val pciename = Some(s"pcie_$i")
    sbus.fromMaster(pciename) { oo.pcieNode }
    sbus.toFixedWidthSlave(pciename) { oo.pcieNode }
    ibus.fromSync := oo.intNode
  }

  // LEDs / GPIOs
  p(PeripheryGPIOKey).zip(p(GPIOOverlayKey)).foreach { case (params, overlay) =>
    val controller = GPIOAttachParams(params).attachTo(this)
    overlay.place(GPIODesignInput(params, controller.ioNode))
  }

  // Grab all the LEDs !!! do something with them
  val leds = p(LEDOverlayKey).map(_.place(LEDDesignInput()).overlayOutput.led)

  val maskROMParams = p(MaskROMLocated(location))
  val maskROMs = maskROMParams.map {MaskROM.attach(_, this, CBUS) }

  val boot = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := boot

  override lazy val module = new U500VC707DevKitSystemModule(this)
}

class U500VC707DevKitSystemModule[+L <: DevKitFPGADesign](_outer: L)
  extends RocketSubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with HasPeripheryDebugModuleImp
{
  // Reset vector is set to the location of the mask rom
  outer.boot.bundle := outer.maskROMParams.head.address.U
}

// Allow frequency of the design to be controlled by the Makefile
class WithDevKitFrequency(MHz: Double) extends Config((site, here, up) => {
  case DevKitFPGAFrequencyKey => MHz
})

class WithDevKit25MHz extends WithDevKitFrequency(25)
class WithDevKit50MHz extends WithDevKitFrequency(50)
class WithDevKit100MHz extends WithDevKitFrequency(100)
class WithDevKit125MHz extends WithDevKitFrequency(125)
class WithDevKit150MHz extends WithDevKitFrequency(150)
class WithDevKit200MHz extends WithDevKitFrequency(200)

class DevKitU500FPGADesign extends Config(
  new U500DevKitConfig().alter((site, here, up) => {
    case DesignKey => { (p:Parameters) => new DevKitWrapper()(p) }
  }))

class WithFPGADevKitDesign extends Config((site, here, up) => {
  case DesignKey => { (p:Parameters) => new DevKitWrapper()(p) }
})

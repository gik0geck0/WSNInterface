Matt Buland's Wireless Sensor Network Interface for Android

# Goals
Have an andoid device to take to a wireless sensor network deployment, and be able to pull
a node's data, graph it, save it, etc. Additional features inculde sending arbitrary strings,
monitoring mote battery, tx/rx levels, etc.

# Helpful resources and Tips

* When connecting to an android device, the IOIO must be externally powered, and have the
  host switch pointed to H, making it a USB host. For the PC, put it in Automatic mode
* Android must have USB debugging disabled to be able to communicate with the IOIO.
* Use App-0330 for new IOIO-OTGs
* Xbee need power on pin 1, UART across 2&3, and ground on 10 (see pinout). rx-xbee -> tx-ioio and viseversa

* Bootloader Firmwares: https://github.com/ytai/ioio/tree/master/release/firmware/bootloader
* IOIO with PC guide: https://github.com/ytai/ioio/wiki/Using-IOIO-With-a-PC
* How to use IOIO Dude to flash firmware images: https://github.com/ytai/ioio/wiki/IOIO-OTG-Bootloader-and-IOIODude
* IOIO Documentation Base: https://github.com/ytai/ioio/wiki
* How to use a UART with UART: https://github.com/ytai/ioio/wiki/UART
* IOIO Pinout: https://github.com/ytai/ioio/wiki/Getting-To-Know-The-IOIO-OTG-Board
* Xbee Pinout: http://code.google.com/p/xbee-api/wiki/XBeePins
* purejavacomm binaries: https://github.com/nyholku/purejavacomm/blob/master/bin/

# Forum Posts With helpful tips (served as sources for above info)
* Turn USB Debugging off: https://groups.google.com/forum/#!msg/ioio-users/O23r_GSMAxw/2OkL5wvcFnQJ
* Use firmware 330 for a new IOIO-OTG: https://groups.google.com/forum/#!msg/ioio-users/vP8LUCPuqDY/zq0Wt6VqVwcJ

/**
 *
 *
 */


metadata {
	definition (name: "Shelly Contact Device", namespace: "ShellyUSA-Custom", author: "Corey Cleric") {
    capability "ContactSensor"
    capability "Actuator"
    attribute "count", "string"
    attribute "lastchange", "string"
	}

preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}
void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
}

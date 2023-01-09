/**
 *   
 *  Shelly Input Driver
 *
 *  Copyright © 2018-2019 Scott Grayban
 *  Copyright © 2020 Allterco Robotics US
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 *  
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 *
 *  Changes:
 *  
 *  1.0.0 - Initial code - Unofficial Custom Driver - Code borrowed and modified to support the Shelly ${state.devicetype} Devices
 *              Removed the Check FW and Upgrade features. /Corey
 *
 */

       

import groovy.json.*
import groovy.transform.Field

def setVersion(){
	state.Version = "1.0.0"
	state.InternalName = "ShellyInputUnofficialDriver"
}

metadata {
	definition (
		name: "Shelly Input Contact Sensor",
		namespace: "ShellyUSA-Custom",
		author: "Scott Grayban / Corey J Cleric"
		)
	{
        capability "Refresh"
        capability "Polling"
        capability "SignalStrength"
        capability "Initialize"


        command "RebootDevice"

        attribute "WiFiSignal", "string"
        
       
}
    

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
    refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		refreshRate << ["manual" : "Manually or Polling Only"]

	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
  input name: "isrounded", type: "bool", title: "Rounded Numbers", defaultValue: true
  input("refresh_Rate", "enum", title: "Device Refresh Rate", description:"<font color=red>!!WARNING!!</font><br>DO NOT USE if you have over 50 Shelly devices.", options: refreshRate, defaultValue: "manual")
  input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}

def initialize() {
	log.info "Shelly ${state.devicetype} IP ${ip} is initializing..."
    getSettings()
    log.info "Shelly ${state.devicetype} IP ${ip} device type = ${state.devicetype} channels = ${state.channels}."
    if (state.channels) {
        String thisId = device.id
        for (int myindex = 1; myindex <= state.channels; myindex++) {
            mychannel = myindex - 1
            state."inputcount${mychannel}" = 0      
            state."inputupdateflag${mychannel}" = true
            if (!getChildDevice("${thisId}-Channel${myindex}")) {
                addChildDevice("ShellyUSA-Custom", "Shelly Contact Device", "${thisId}-Channel${myindex}", [name: "Channel${myindex}", isComponent: true])
                log.info "Shelly ${state.devicetype} IP ${ip} installing child ${thisId}-Channel${myindex}."
                }
            }
        }    
    runIn(2,getContactConfig)
    runIn(10,getContactStatusAll)
}

def installed() {
    log.debug "Shelly ${state.devicetype} Meter IP ${ip} installed."
    state.DeviceName = "NotSet"
}

def uninstalled() {
    unschedule()    
    removeChildDevices(getChildDevices())
    log.debug "Shelly ${state.devicetype} IP ${ip} uninstalled."
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def updated() {
    if (txtEnable) log.info "Shelly ${state.devicetype} IP ${ip} preferences updated."
    log.warn "Shelly ${state.devicetype} IP ${ip} debug logging is: ${debugOutput == true}"
    unschedule()
    dbCleanUp()
    
    switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(autorefresh)
			break
        case "5 min" :
			runEvery5Minutes(autorefresh)
			break
		case "15 min" :
			runEvery15Minutes(autorefresh)
			break
		case "30 min" :
			runEvery30Minutes(autorefresh)
			break
		case "manual" :
			unschedule(autorefresh)
            log.info "Autorefresh disabled"
            break
	}
	if (txtEnable) log.debug ("Shelly ${state.devicetype} IP ${ip} auto Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff) //Off in 30 minutes
    if (debugParse) runIn(300,logsOff) //Off in 5 minutes
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    refresh()
}

private dbCleanUp() {
    state.clear()
}

def refresh() {
    logDebug "Shelly ${state.devicetype} IP ${ip} refresh."
    getContactConfig()
    getContactStatusAll()
}

def getContactConfig() {
    if (state.channels) {
        for (int myindex = 0; myindex < state.channels; myindex++) {
            def params = [uri: "http://${username}:${password}@${ip}/rpc/Input.GetConfig?id=${myindex}"]
            try {
                httpGet(params) { resp -> resp.headers.each { logJSON "Shelly ${state.devicetype} IP ${ip} response: ${it.name} : ${it.value}" }
                obs = resp.data
                logJSON "Shelly ${state.devicetype} IP ${ip} params: ${params}"
                logJSON "Shelly ${state.devicetype} IP ${ip} response contentType: ${resp.contentType}"
	            logJSON "Shelly ${state.devicetype} IP ${ip} response data: ${resp.data}"
                state."inputtype${myindex}" = obs.type
                state."inputname${myindex}" = obs.name
                state."inputinvert${myindex}" = obs.invert
                } // End try
            } catch (e) { log.error "Shelly ${state.devicetype} IP ${ip} getContactStatus something went wrong: $e" }
        }    
    }    
    //runIn(3,updateChildren)
} // End getContactConfig

def getContactChannelStatus(myindex) {
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Input.GetStatus?id=${myindex}"]
    try {
        httpGet(params) { resp -> resp.headers.each { logJSON "Shelly ${state.devicetype} IP ${ip} response: ${it.name} : ${it.value}" }
        obs = resp.data
        logJSON "Shelly ${state.devicetype} IP ${ip} params: ${params}"
        logJSON "Shelly ${state.devicetype} IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly ${state.devicetype} IP ${ip} response data: ${resp.data}"
        if (obs.state != state."inputstate${myindex}") {
            log.info "Shelly ${state.devicetype} IP ${ip} getContactChannelStatus detected state change Channel ${myindex}"
            state."inputstate${myindex}" = obs.state
            state."inputupdateflag${myindex}" = true
            }
        } // End try
   } catch (e) { log.error "Shelly ${state.devicetype} IP ${ip} getContactChannelStatus something went wrong: $e" }
} // End getContactChannelStatus


def getContactStatusAll() {
    if (state.channels) {
        for (int myindex = 0; myindex < state.channels; myindex++) { getContactChannelStatus(myindex) }
    }    
    runIn(3,updateChildren)
} // End getContactStatusAll

def updateChildren() {
    if (state.channels) {
        String thisId = device.id
        for (int myindex = 1; myindex <= state.channels; myindex++) {
            child = getChildDevice("${thisId}-Channel${myindex}")
            if (child) {
                if (state."inputupdateflag${mychannel}" == true) { 
                   if (state."inputtype${mychannel}" == "switch" && state."inputstate${mychannel}" == true) {
                        child.sendEvent(name: "contact", value: "active") 
                        //child.sendEvent(name: "lastchange", value: getDateTime()) 
                        child.sendEvent(name: "count", value: state."inputcount${mychannel}") 
                        state."inputtype${mychannel}" = false
                   }     
                if (state."inputtype${mychannel}" == "switch" && state."inputstate${mychannel}" == false ) {
                        child.sendEvent(name: "contact", value: "inactive") 
                        state."inputtype${mychannel}" = false
                        //child.sendEvent(name: "lastchange", value: getDateTime()) 
                        child.sendEvent(name: "count", value: state."inputcount${mychannel}") 
                   }
               
               state."inputupdateflag${mychannel}" = false
               }
            } else { log.error "Shelly ${state.devicetype} IP ${ip} updateChildren something went wrong: $e re-initialize please." }
        }
    }
}

def updateChild(mychannel) {
    if (state.channels) {
        String thisId = device.id
        myindex = "${mychannel}"; myindex++
        child = getChildDevice("${thisId}-Channel${myindex}")
        //log.info "Shelly ${state.devicetype} IP ${ip} myindex = ${myindex} mychannel = ${mychannel} child = ${child}"
        if (child) {
            if (state."inputupdateflag${mychannel}" == true) { 
                if (state."inputtype${mychannel}" == "switch" && state."inputstate${mychannel}" == false) {
                   child.sendEvent(name: "contact", value: "inactive") 
                   child.sendEvent(name: "count", value: state."inputcount${mychannel}") 
                   //child.sendEvent(name: "lastchange", value: getDateTime()) 
               }
                if (state."inputtype${mychannel}" == "switch" && state."inputstate${mychannel}" == true) {
                   child.sendEvent(name: "contact", value: "active") 
                   child.sendEvent(name: "count", value: state."inputcount${mychannel}") 
                   //child.sendEvent(name: "lastchange", value: getDateTime()) 
                   }
                }
              state."inputupdateflag${mychannel}" = false
              if (state."inputtype${mychannel}" == "button") {
                   child.sendEvent(name: "contact", value: "active") 
                   child.sendEvent(name: "count", value: state."inputcount${mychannel}") 
                   //child.sendEvent(name: "lastchange", value: getDateTime() ) 
                   runIn(3,updateChildInactive)
                   }
        } else { log.error "Shelly ${state.devicetype} IP ${ip} updateChild something went wrong: $e re-initialize please." }
        
    }
}

def updateChildInactive() {
    if (state.channels) {
        String thisId = device.id
        for (int myindex = 1; myindex <= state.channels; myindex++) {
            mychannel = myindex - 1
            child = getChildDevice("${thisId}-Channel${myindex}")
            //log.info "Shelly ${state.devicetype} IP ${ip} myindex = ${myindex} mychannel = ${mychannel} child = ${child}"
            if (child) {
                if (state."inputtype${mychannel}" == "button") { child.sendEvent(name: "contact", value: "inactive") }
            } else { log.error "Shelly ${state.devicetype} IP ${ip} updateChildInactive something went wrong: $e re-initialize please." }
        }
    }
}

def getSettings(){

    logDebug "Shelly ${state.devicetype} IP ${ip} get settings called"
    //getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.GetDeviceInfo"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Shelly ${state.devicetype} IP ${ip} response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "Shelly ${state.devicetype} IP ${ip} params: ${params}"
        logJSON "Shelly ${state.devicetype} IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly ${state.devicetype} IP ${ip} response data: ${resp.data}"

        
        updateDataValue("Device Name", obs.name)
        updateDataValue("Device ID", obs.id)
        updateDataValue("FW ID", obs.fw_id)
        updateDataValue("FW Version", obs.ver)
        updateDataValue("Application", obs.app)
        updateDataValue("Model", obs.model)
        updateDataValue("MAC", obs.mac)
        
        state.devicetype = obs.model
        if (state.devicetype == "SNSN-0024X") { state.channels = 4 } 
        else { state.channels = 0 }
        
    } // End try
       } catch (e) {
           log.error "Shelly ${state.devicetype} IP ${ip} getSettings something went wrong: $e"
       }
    
} // End Device Info


// Parse incoming device messages to generate events
def parse(String description) {
    log.info "Shelly ${state.devicetype} IP ${ip} recieved callback message."
    //log.info "Shelly ${state.devicetype} IP ${ip} Description = ${description}"
    def msg = parseLanMessage(description)
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
    //log.info "headersAsString = ${headersAsString} headerMap = ${headerMap} Body = ${body} Status = ${status} Data = ${data}"
    try { 
        mycmds2 = msg.header.split(); mycmd1 = mycmds2[0]; myaction = mycmds2[1].split('/')
        myactionchannel = myaction[1]; myactionevent = myaction[2]
        //log.info "Shelly ${state.devicetype} IP ${ip} Command = ${mycmd1} Action Channel = ${myactionchannel} Action Event = ${myactionevent}"
        } // end try
     catch (e) { log.error "Shelly ${state.devicetype} IP ${ip} parse something went wrong: $e"; return }
     state."inputcount${myactionchannel}"++
     if (myactionevent == "on" || myactionevent == "off") { 
         getContactChannelStatus(myactionchannel) 
         updateChild(myactionchannel)
         }
     if (myactionevent == "push" ) { 
         state."inputupdateflag${mychannel}" = true
         updateChild(myactionchannel) 
     }
}


def ping() {
	logDebug "Shelly ${state.devicetype} IP ${ip} recieved ping."
	poll()
}

def logsOff() {
	log.warn "Shelly ${state.devicetype} IP ${ip} debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def autorefresh() {
    if (locale == "UK") {
	logDebug "Shelly ${state.devicetype} IP ${ip} Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Shelly ${state.devicetype} IP ${ip} Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Shelly ${state.devicetype} IP ${ip} executing 'auto refresh'" //RK
    refresh()
}


def getDateTime() {
    mytime = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
    log.info "Shelly ${state.devicetype} IP ${ip} mytime = ${mytime}"
    return mytime
}

private logJSON(msg) {
	if (settings?.debugParse || settings?.debugParse == null) {
	log.info "Shelly ${state.devicetype} IP ${ip} $msg"
	}
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "Shelly ${state.devicetype} IP ${ip} $msg"
	}
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	logDebug "Shelly ${state.devicetype} IP ${ip} Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Shelly ${state.devicetype} IP ${ip} Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Shelly ${state.devicetype} IP ${ip} executing 'poll'" //RK
	refresh()
}



def RebootDevice() {
    if (txtEnable) log.info "Shelly ${state.devicetype} IP ${ip} rebooting device"
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.Reboot"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Shelly ${state.devicetype} IP ${ip} response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "Shelly ${state.devicetype} IP ${ip} something went wrong: $e"
    }
    runIn(15,refresh)
}


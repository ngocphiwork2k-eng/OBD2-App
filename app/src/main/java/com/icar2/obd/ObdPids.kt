package com.icar2.obd

// Định nghĩa toàn bộ PID Mode 01 phổ biến + công thức SAE J1979.
data class PidDef(
    val mode: String, // "01"
    val pid: String,  // "0C"
    val name: String,
    val unit: String,
    val parse: (bytes: List<Int>) -> Double?
)

private fun b(d: List<Int>, i: Int) = d.getOrNull(i) ?: 0

object ObdPids {
    val ALL_01 = listOf(
        PidDef("01","04","Engine Load","%", { b(it,0)*100/255.0 }),
        PidDef("01","05","Coolant Temp","°C", { b(it,0)-40.0 }),
        PidDef("01","0A","Fuel Pressure","kPa", { b(it,0)*3.0 }),
        PidDef("01","0B","MAP","kPa", { b(it,0).toDouble() }),
        PidDef("01","0C","RPM","rpm", { ((b(it,0)*256+b(it,1))/4.0) }),
        PidDef("01","0D","Speed","km/h", { b(it,0).toDouble() }),
        PidDef("01","0E","Timing Advance","°", { b(it,0)/2.0-64 }),
        PidDef("01","0F","Intake Air Temp","°C", { b(it,0)-40.0 }),
        PidDef("01","10","MAF","g/s", { ((b(it,0)*256+b(it,1))/100.0) }),
        PidDef("01","11","Throttle Pos","%", { b(it,0)*100/255.0 }),
        PidDef("01","06","Short Fuel Trim B1","%", { b(it,0)/1.28-100 }),
        PidDef("01","07","Long Fuel Trim B1","%", { b(it,0)/1.28-100 }),
        PidDef("01","08","Short Fuel Trim B2","%", { b(it,0)/1.28-100 }),
        PidDef("01","09","Long Fuel Trim B2","%", { b(it,0)/1.28-100 }),
        PidDef("01","0F","IAT","°C", { b(it,0)-40.0 }),
        PidDef("01","21","MIL Distance","km", { (b(it,0)*256+b(it,1)).toDouble() }),
        PidDef("01","2F","Fuel Level","%", { b(it,0)*100/255.0 }),
        PidDef("01","31","DTC Clear Distance","km", { (b(it,0)*256+b(it,1)).toDouble() }),
        PidDef("01","33","Baro Pressure","kPa", { b(it,0).toDouble() }),
        PidDef("01","42","Control Module Voltage","V", { ((b(it,0)*256+b(it,1))/1000.0) }),
        PidDef("01","45","Relative Throttle","%", { b(it,0)*100/255.0 }),
        PidDef("01","5C","Engine Oil Temp","°C", { b(it,0)-40.0 }),
        PidDef("01","5E","Engine Fuel Rate","L/h", { ((b(it,0)*256+b(it,1))/20.0) }),
        PidDef("01","2C","Commanded EGR","%", { b(it,0)*100/255.0 }),
    )

    val SUPPORTED_QUERIES = listOf("00","20","40","60","80","A0","C0","E0")

    fun parseFrame(hex: String): Map<String, Double?> {
        // VD: "41 0C 1A F8" -> RPM
        val clean = hex.replace(">"," ").replace("SEARCHING..."," ")
            .split("\r","\n"," ").map { it.trim() }.filter { it.length==2 }
        if (clean.size < 3) return emptyMap()
        // Bỏ echo + xử multi-frame đơn giản: tìm "41 <PID> ..."
        val out = mutableMapOf<String, Double?>()
        var i = 0
        while (i < clean.size - 2) {
            if (clean[i].equals("41", true)) {
                val pid = clean[i+1].uppercase()
                val def = ALL_01.find { it.pid == pid }
                if (def != null) {
                    val bytes = clean.drop(i+2).take(4).mapNotNull { it.toIntOrNull(16) }
                    try { out[pid] = def.parse(bytes) } catch (_: Exception) {}
                }
                i += 2
            } else i++
        }
        return out
    }

    // Giải mã bitmask 0100 -> list PID hỗ trợ. VD byte0=BE -> PID 01,03,04,05,06,07 hỗ trợ
    fun decodeSupported(hex: String): Set<String> {
        val bytes = hex.split(" ","\r","\n").mapNotNull { it.toIntOrNull(16) }
        // tìm sau "41 00"
        val idx = bytes.windowed(2).indexOfFirst { it[0]==0x41 && it[1]==0x00 }
        if (idx == -1) return emptySet()
        val data = bytes.drop(idx+2).take(4)
        val set = mutableSetOf<String>()
        data.forEachIndexed { bi, byte ->
            for (bit in 0..7) {
                if (byte and (0x80 shr bit) != 0) {
                    set.add("%02X".format(bi*8+bit+1))
                }
            }
        }
        return set
    }
}

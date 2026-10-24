package com.swiftshare.common

object Constants {
    const val PORT_CONTROL_HTTP = 2999
    const val PORT_DATA_NIO = 2998
    const val PORT_UDP_BROADCAST = 55283

    const val NSD_SERVICE_TYPE = "_swiftshare._tcp."
    const val NSD_SERVICE_NAME = "SwiftShareNode"

    const val NOTIFICATION_CHANNEL_TRANSFER = "swiftshare_transfers"
    const val NOTIFICATION_CHANNEL_SERVICE = "swiftshare_service"
    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_TRANSFER = 1002

    const val BLE_SERVICE_UUID = "0000FE28-0000-1000-8000-00805F9B34FB"
    const val BLE_CHAR_CREDENTIALS = "0000FE29-0000-1000-8000-00805F9B34FB"

    const val ACTION_START_TRANSFER = "com.swiftshare.action.START_TRANSFER"
    const val ACTION_CANCEL_TRANSFER = "com.swiftshare.action.CANCEL_TRANSFER"

    // Ultra-fast 5GHz socket settings
    const val SOCKET_BUFFER_SIZE = 1024 * 1024      // 1 MB — maximum Wi-Fi 5GHz throughput
    const val TRANSFER_CHUNK_SIZE = 512 * 1024       // 512 KB zero-copy FileChannel chunks
    const val SOCKET_TIMEOUT_MS = 15_000             // 15 s socket timeout

    @Deprecated("Use SOCKET_BUFFER_SIZE for send/receive", replaceWith = ReplaceWith("SOCKET_BUFFER_SIZE"))
    const val BUFFER_SIZE_NIO = 128 * 1024           // kept for compatibility

    const val PREF_DEVICE_NAME = "pref_device_name"
    const val PREF_AVATAR_ID = "pref_avatar_id"
}

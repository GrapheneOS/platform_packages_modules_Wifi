/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi.nl80211;

/**
 * Constants used by Netlink and Nl80211.
 */
public class NetlinkConstants {
    // Socket levels. See libc/include/sys/socket.h
    public static final int SOL_NETLINK = 270;

    // Netlink protocols. See kernel/uapi/linux/netlink.h
    public static final int NETLINK_GENERIC = 16;

    // Netlink socket options. See kernel/uapi/linux/netlink.h
    public static final int NETLINK_ADD_MEMBERSHIP = 1;

    // Control message types. See kernel/uapi/linux/genetlink.h
    public static final short GENL_ID_CTRL = 0x10;

    // Control message commands. See kernel/uapi/linux/genetlink.h
    public static final short CTRL_CMD_NEWFAMILY = 1;
    public static final short CTRL_CMD_GETFAMILY = 3;

    // Control message attributes. See kernel/uapi/linux/genetlink.h
    public static final short CTRL_ATTR_FAMILY_ID = 1;
    public static final short CTRL_ATTR_FAMILY_NAME = 2;

    public static final short CTRL_ATTR_MCAST_GRP_NAME = 1;
    public static final short CTRL_ATTR_MCAST_GRP_ID = 2;

    public static final short CTRL_ATTR_MCAST_GROUPS = 7;

    // Netlink message types. See kernel/uapi/linux/netlink.h
    public static final short NLMSG_ERROR = 2;
    public static final short NLMSG_DONE = 3;

    // Nl80211 strings for initialization. See kernel/uapi/linux/nl80211.h
    public static final String NL80211_GENL_NAME = "nl80211";
    public static final String NL80211_MULTICAST_GROUP_SCAN = "scan";
    public static final String NL80211_MULTICAST_GROUP_REG = "regulatory";
    public static final String NL80211_MULTICAST_GROUP_MLME = "mlme";

    // Split wiphy dump protocol feature. See kernel/uapi/linux/nl80211.h
    public static final int NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP = 1 << 0;

    // Nl80211 commands. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_CMD_UNSPEC = 0;
    public static final short NL80211_CMD_GET_WIPHY = 1; /* can dump */
    public static final short NL80211_CMD_SET_WIPHY = 2;
    public static final short NL80211_CMD_NEW_WIPHY = 3;
    public static final short NL80211_CMD_DEL_WIPHY = 4;

    public static final short NL80211_CMD_GET_INTERFACE = 5; /* can dump */
    public static final short NL80211_CMD_SET_INTERFACE = 6;
    public static final short NL80211_CMD_NEW_INTERFACE = 7;
    public static final short NL80211_CMD_DEL_INTERFACE = 8;

    public static final short NL80211_CMD_GET_KEY = 9;
    public static final short NL80211_CMD_SET_KEY = 10;
    public static final short NL80211_CMD_NEW_KEY = 11;
    public static final short NL80211_CMD_DEL_KEY = 12;

    public static final short NL80211_CMD_GET_BEACON = 13;
    public static final short NL80211_CMD_SET_BEACON = 14;
    public static final short NL80211_CMD_START_AP = 15;
    public static final short NL80211_CMD_STOP_AP = 16;

    public static final short NL80211_CMD_GET_STATION = 17;
    public static final short NL80211_CMD_SET_STATION = 18;
    public static final short NL80211_CMD_NEW_STATION = 19;
    public static final short NL80211_CMD_DEL_STATION = 20;

    public static final short NL80211_CMD_GET_MPATH = 21;
    public static final short NL80211_CMD_SET_MPATH = 22;
    public static final short NL80211_CMD_NEW_MPATH = 23;
    public static final short NL80211_CMD_DEL_MPATH = 24;

    public static final short NL80211_CMD_SET_BSS = 25;

    public static final short NL80211_CMD_SET_REG = 26;
    public static final short NL80211_CMD_REQ_SET_REG = 27;

    public static final short NL80211_CMD_GET_MESH_CONFIG = 28;
    public static final short NL80211_CMD_SET_MESH_CONFIG = 29;

    public static final short NL80211_CMD_SET_MGMT_EXTRA_IE = 30; /* reserved; not used */

    public static final short NL80211_CMD_GET_REG = 31;

    public static final short NL80211_CMD_GET_SCAN = 32;
    public static final short NL80211_CMD_TRIGGER_SCAN = 33;
    public static final short NL80211_CMD_NEW_SCAN_RESULTS = 34;
    public static final short NL80211_CMD_SCAN_ABORTED = 35;

    public static final short NL80211_CMD_REG_CHANGE = 36;

    public static final short NL80211_CMD_AUTHENTICATE = 37;
    public static final short NL80211_CMD_ASSOCIATE = 38;
    public static final short NL80211_CMD_DEAUTHENTICATE = 39;
    public static final short NL80211_CMD_DISASSOCIATE = 40;

    public static final short NL80211_CMD_MICHAEL_MIC_FAILURE = 41;

    public static final short NL80211_CMD_REG_BEACON_HINT = 42;

    public static final short NL80211_CMD_JOIN_IBSS = 43;
    public static final short NL80211_CMD_LEAVE_IBSS = 44;

    public static final short NL80211_CMD_TESTMODE = 45;

    public static final short NL80211_CMD_CONNECT = 46;
    public static final short NL80211_CMD_ROAM = 47;
    public static final short NL80211_CMD_DISCONNECT = 48;

    public static final short NL80211_CMD_SET_WIPHY_NETNS = 49;

    public static final short NL80211_CMD_GET_SURVEY = 50;
    public static final short NL80211_CMD_NEW_SURVEY_RESULTS = 51;

    public static final short NL80211_CMD_SET_PMKSA = 52;
    public static final short NL80211_CMD_DEL_PMKSA = 53;
    public static final short NL80211_CMD_FLUSH_PMKSA = 54;

    public static final short NL80211_CMD_REMAIN_ON_CHANNEL = 55;
    public static final short NL80211_CMD_CANCEL_REMAIN_ON_CHANNEL = 56;

    public static final short NL80211_CMD_SET_TX_BITRATE_MASK = 57;

    public static final short NL80211_CMD_REGISTER_FRAME = 58;
    public static final short NL80211_CMD_FRAME = 59;
    public static final short NL80211_CMD_FRAME_TX_STATUS = 60;

    public static final short NL80211_CMD_SET_POWER_SAVE = 61;
    public static final short NL80211_CMD_GET_POWER_SAVE = 62;

    public static final short NL80211_CMD_SET_CQM = 63;
    public static final short NL80211_CMD_NOTIFY_CQM = 64;

    public static final short NL80211_CMD_SET_CHANNEL = 65;
    public static final short NL80211_CMD_SET_WDS_PEER = 66;

    public static final short NL80211_CMD_FRAME_WAIT_CANCEL = 67;

    public static final short NL80211_CMD_JOIN_MESH = 68;
    public static final short NL80211_CMD_LEAVE_MESH = 69;

    public static final short NL80211_CMD_UNPROT_DEAUTHENTICATE = 70;
    public static final short NL80211_CMD_UNPROT_DISASSOCIATE = 71;

    public static final short NL80211_CMD_NEW_PEER_CANDIDATE = 72;

    public static final short NL80211_CMD_GET_WOWLAN = 73;
    public static final short NL80211_CMD_SET_WOWLAN = 74;

    public static final short NL80211_CMD_START_SCHED_SCAN = 75;
    public static final short NL80211_CMD_STOP_SCHED_SCAN = 76;
    public static final short NL80211_CMD_SCHED_SCAN_RESULTS = 77;
    public static final short NL80211_CMD_SCHED_SCAN_STOPPED = 78;

    public static final short NL80211_CMD_SET_REKEY_OFFLOAD = 79;

    public static final short NL80211_CMD_PMKSA_CANDIDATE = 80;

    public static final short NL80211_CMD_TDLS_OPER = 81;
    public static final short NL80211_CMD_TDLS_MGMT = 82;

    public static final short NL80211_CMD_UNEXPECTED_FRAME = 83;

    public static final short NL80211_CMD_PROBE_CLIENT = 84;

    public static final short NL80211_CMD_REGISTER_BEACONS = 85;

    public static final short NL80211_CMD_UNEXPECTED_4ADDR_FRAME = 86;

    public static final short NL80211_CMD_SET_NOACK_MAP = 87;

    public static final short NL80211_CMD_CH_SWITCH_NOTIFY = 88;

    public static final short NL80211_CMD_START_P2P_DEVICE = 89;
    public static final short NL80211_CMD_STOP_P2P_DEVICE = 90;

    public static final short NL80211_CMD_CONN_FAILED = 91;

    public static final short NL80211_CMD_SET_MCAST_RATE = 92;

    public static final short NL80211_CMD_SET_MAC_ACL = 93;

    public static final short NL80211_CMD_RADAR_DETECT = 94;

    public static final short NL80211_CMD_GET_PROTOCOL_FEATURES = 95;

    public static final short NL80211_CMD_UPDATE_FT_IES = 96;
    public static final short NL80211_CMD_FT_EVENT = 97;

    public static final short NL80211_CMD_CRIT_PROTOCOL_START = 98;
    public static final short NL80211_CMD_CRIT_PROTOCOL_STOP = 99;

    public static final short NL80211_CMD_GET_COALESCE = 100;
    public static final short NL80211_CMD_SET_COALESCE = 101;

    public static final short NL80211_CMD_CHANNEL_SWITCH = 102;

    public static final short NL80211_CMD_VENDOR = 103;

    public static final short NL80211_CMD_SET_QOS_MAP = 104;

    public static final short NL80211_CMD_ADD_TX_TS = 105;
    public static final short NL80211_CMD_DEL_TX_TS = 106;

    public static final short NL80211_CMD_GET_MPP = 107;

    public static final short NL80211_CMD_JOIN_OCB = 108;
    public static final short NL80211_CMD_LEAVE_OCB = 109;

    public static final short NL80211_CMD_CH_SWITCH_STARTED_NOTIFY = 110;

    public static final short NL80211_CMD_TDLS_CHANNEL_SWITCH = 111;
    public static final short NL80211_CMD_TDLS_CANCEL_CHANNEL_SWITCH = 112;

    public static final short NL80211_CMD_WIPHY_REG_CHANGE = 113;

    public static final short NL80211_CMD_ABORT_SCAN = 114;

    public static final short NL80211_CMD_START_NAN = 115;
    public static final short NL80211_CMD_STOP_NAN = 116;
    public static final short NL80211_CMD_ADD_NAN_FUNCTION = 117;
    public static final short NL80211_CMD_DEL_NAN_FUNCTION = 118;
    public static final short NL80211_CMD_CHANGE_NAN_CONFIG = 119;
    public static final short NL80211_CMD_NAN_MATCH = 120;

    public static final short NL80211_CMD_SET_MULTICAST_TO_UNICAST = 121;

    public static final short NL80211_CMD_UPDATE_CONNECT_PARAMS = 122;

    public static final short NL80211_CMD_SET_PMK = 123;
    public static final short NL80211_CMD_DEL_PMK = 124;

    public static final short NL80211_CMD_PORT_AUTHORIZED = 125;

    public static final short NL80211_CMD_RELOAD_REGDB = 126;

    public static final short NL80211_CMD_EXTERNAL_AUTH = 127;

    public static final short NL80211_CMD_STA_OPMODE_CHANGED = 128;

    public static final short NL80211_CMD_CONTROL_PORT_FRAME = 129;

    public static final short NL80211_CMD_GET_FTM_RESPONDER_STATS = 130;

    public static final short NL80211_CMD_PEER_MEASUREMENT_START = 131;
    public static final short NL80211_CMD_PEER_MEASUREMENT_RESULT = 132;
    public static final short NL80211_CMD_PEER_MEASUREMENT_COMPLETE = 133;

    public static final short NL80211_CMD_NOTIFY_RADAR = 134;

    public static final short NL80211_CMD_UPDATE_OWE_INFO = 135;

    public static final short NL80211_CMD_PROBE_MESH_LINK = 136;

    public static final short NL80211_CMD_SET_TID_CONFIG = 137;

    public static final short NL80211_CMD_UNPROT_BEACON = 138;

    public static final short NL80211_CMD_CONTROL_PORT_FRAME_TX_STATUS = 139;

    public static final short NL80211_CMD_SET_SAR_SPECS = 140;

    public static final short NL80211_CMD_OBSS_COLOR_COLLISION = 141;

    public static final short NL80211_CMD_COLOR_CHANGE_REQUEST = 142;

    public static final short NL80211_CMD_COLOR_CHANGE_STARTED = 143;
    public static final short NL80211_CMD_COLOR_CHANGE_ABORTED = 144;
    public static final short NL80211_CMD_COLOR_CHANGE_COMPLETED = 145;

    public static final short NL80211_CMD_SET_FILS_AAD = 146;

    public static final short NL80211_CMD_ASSOC_COMEBACK = 147;

    public static final short NL80211_CMD_ADD_LINK = 148;
    public static final short NL80211_CMD_REMOVE_LINK = 149;

    public static final short NL80211_CMD_ADD_LINK_STA = 150;
    public static final short NL80211_CMD_MODIFY_LINK_STA = 151;
    public static final short NL80211_CMD_REMOVE_LINK_STA = 152;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_1 = 153;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_2 = 154;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_3 = 155;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_4 = 156;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_5 = 157;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_6 = 158;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_7 = 159;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_8 = 160;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_9 = 161;
    public static final short NL80211_CMD_ANDROID_KABI_RESERVED_10 = 162;

    // Nl80211 attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_ATTR_UNSPEC = 0;

    public static final short NL80211_ATTR_WIPHY = 1;
    public static final short NL80211_ATTR_WIPHY_NAME = 2;

    public static final short NL80211_ATTR_IFINDEX = 3;
    public static final short NL80211_ATTR_IFNAME = 4;
    public static final short NL80211_ATTR_IFTYPE = 5;

    public static final short NL80211_ATTR_MAC = 6;

    public static final short NL80211_ATTR_KEY_DATA = 7;
    public static final short NL80211_ATTR_KEY_IDX = 8;
    public static final short NL80211_ATTR_KEY_CIPHER = 9;
    public static final short NL80211_ATTR_KEY_SEQ = 10;
    public static final short NL80211_ATTR_KEY_DEFAULT = 11;

    public static final short NL80211_ATTR_BEACON_INTERVAL = 12;
    public static final short NL80211_ATTR_DTIM_PERIOD = 13;
    public static final short NL80211_ATTR_BEACON_HEAD = 14;
    public static final short NL80211_ATTR_BEACON_TAIL = 15;

    public static final short NL80211_ATTR_STA_AID = 16;
    public static final short NL80211_ATTR_STA_FLAGS = 17;
    public static final short NL80211_ATTR_STA_LISTEN_INTERVAL = 18;
    public static final short NL80211_ATTR_STA_SUPPORTED_RATES = 19;
    public static final short NL80211_ATTR_STA_VLAN = 20;
    public static final short NL80211_ATTR_STA_INFO = 21;

    public static final short NL80211_ATTR_WIPHY_BANDS = 22;

    public static final short NL80211_ATTR_MNTR_FLAGS = 23;

    public static final short NL80211_ATTR_MESH_ID = 24;
    public static final short NL80211_ATTR_STA_PLINK_ACTION = 25;
    public static final short NL80211_ATTR_MPATH_NEXT_HOP = 26;
    public static final short NL80211_ATTR_MPATH_INFO = 27;

    public static final short NL80211_ATTR_BSS_CTS_PROT = 28;
    public static final short NL80211_ATTR_BSS_SHORT_PREAMBLE = 29;
    public static final short NL80211_ATTR_BSS_SHORT_SLOT_TIME = 30;

    public static final short NL80211_ATTR_HT_CAPABILITY = 31;

    public static final short NL80211_ATTR_SUPPORTED_IFTYPES = 32;

    public static final short NL80211_ATTR_REG_ALPHA2 = 33;
    public static final short NL80211_ATTR_REG_RULES = 34;

    public static final short NL80211_ATTR_MESH_CONFIG = 35;

    public static final short NL80211_ATTR_BSS_BASIC_RATES = 36;

    public static final short NL80211_ATTR_WIPHY_TXQ_PARAMS = 37;
    public static final short NL80211_ATTR_WIPHY_FREQ = 38;
    public static final short NL80211_ATTR_WIPHY_CHANNEL_TYPE = 39;

    public static final short NL80211_ATTR_KEY_DEFAULT_MGMT = 40;

    public static final short NL80211_ATTR_MGMT_SUBTYPE = 41;
    public static final short NL80211_ATTR_IE = 42;

    public static final short NL80211_ATTR_MAX_NUM_SCAN_SSIDS = 43;

    public static final short NL80211_ATTR_SCAN_FREQUENCIES = 44;
    public static final short NL80211_ATTR_SCAN_SSIDS = 45;
    public static final short NL80211_ATTR_GENERATION = 46; /* replaces old SCAN_GENERATION */
    public static final short NL80211_ATTR_BSS = 47;

    public static final short NL80211_ATTR_REG_INITIATOR = 48;
    public static final short NL80211_ATTR_REG_TYPE = 49;

    public static final short NL80211_ATTR_SUPPORTED_COMMANDS = 50;

    public static final short NL80211_ATTR_FRAME = 51;
    public static final short NL80211_ATTR_SSID = 52;
    public static final short NL80211_ATTR_AUTH_TYPE = 53;
    public static final short NL80211_ATTR_REASON_CODE = 54;

    public static final short NL80211_ATTR_KEY_TYPE = 55;

    public static final short NL80211_ATTR_MAX_SCAN_IE_LEN = 56;
    public static final short NL80211_ATTR_CIPHER_SUITES = 57;

    public static final short NL80211_ATTR_FREQ_BEFORE = 58;
    public static final short NL80211_ATTR_FREQ_AFTER = 59;

    public static final short NL80211_ATTR_FREQ_FIXED = 60;


    public static final short NL80211_ATTR_WIPHY_RETRY_SHORT = 61;
    public static final short NL80211_ATTR_WIPHY_RETRY_LONG = 62;
    public static final short NL80211_ATTR_WIPHY_FRAG_THRESHOLD = 63;
    public static final short NL80211_ATTR_WIPHY_RTS_THRESHOLD = 64;

    public static final short NL80211_ATTR_TIMED_OUT = 65;

    public static final short NL80211_ATTR_USE_MFP = 66;

    public static final short NL80211_ATTR_STA_FLAGS2 = 67;

    public static final short NL80211_ATTR_CONTROL_PORT = 68;

    public static final short NL80211_ATTR_TESTDATA = 69;

    public static final short NL80211_ATTR_PRIVACY = 70;

    public static final short NL80211_ATTR_DISCONNECTED_BY_AP = 71;
    public static final short NL80211_ATTR_STATUS_CODE = 72;

    public static final short NL80211_ATTR_CIPHER_SUITES_PAIRWISE = 73;
    public static final short NL80211_ATTR_CIPHER_SUITE_GROUP = 74;
    public static final short NL80211_ATTR_WPA_VERSIONS = 75;
    public static final short NL80211_ATTR_AKM_SUITES = 76;

    public static final short NL80211_ATTR_REQ_IE = 77;
    public static final short NL80211_ATTR_RESP_IE = 78;

    public static final short NL80211_ATTR_PREV_BSSID = 79;

    public static final short NL80211_ATTR_KEY = 80;
    public static final short NL80211_ATTR_KEYS = 81;

    public static final short NL80211_ATTR_PID = 82;

    public static final short NL80211_ATTR_4ADDR = 83;

    public static final short NL80211_ATTR_SURVEY_INFO = 84;

    public static final short NL80211_ATTR_PMKID = 85;
    public static final short NL80211_ATTR_MAX_NUM_PMKIDS = 86;

    public static final short NL80211_ATTR_DURATION = 87;

    public static final short NL80211_ATTR_COOKIE = 88;

    public static final short NL80211_ATTR_WIPHY_COVERAGE_CLASS = 89;

    public static final short NL80211_ATTR_TX_RATES = 90;

    public static final short NL80211_ATTR_FRAME_MATCH = 91;

    public static final short NL80211_ATTR_ACK = 92;

    public static final short NL80211_ATTR_PS_STATE = 93;

    public static final short NL80211_ATTR_CQM = 94;

    public static final short NL80211_ATTR_LOCAL_STATE_CHANGE = 95;

    public static final short NL80211_ATTR_AP_ISOLATE = 96;

    public static final short NL80211_ATTR_WIPHY_TX_POWER_SETTING = 97;
    public static final short NL80211_ATTR_WIPHY_TX_POWER_LEVEL = 98;

    public static final short NL80211_ATTR_TX_FRAME_TYPES = 99;
    public static final short NL80211_ATTR_RX_FRAME_TYPES = 100;
    public static final short NL80211_ATTR_FRAME_TYPE = 101;

    public static final short NL80211_ATTR_CONTROL_PORT_ETHERTYPE = 102;
    public static final short NL80211_ATTR_CONTROL_PORT_NO_ENCRYPT = 103;

    public static final short NL80211_ATTR_SUPPORT_IBSS_RSN = 104;

    public static final short NL80211_ATTR_WIPHY_ANTENNA_TX = 105;
    public static final short NL80211_ATTR_WIPHY_ANTENNA_RX = 106;

    public static final short NL80211_ATTR_MCAST_RATE = 107;

    public static final short NL80211_ATTR_OFFCHANNEL_TX_OK = 108;

    public static final short NL80211_ATTR_BSS_HT_OPMODE = 109;

    public static final short NL80211_ATTR_KEY_DEFAULT_TYPES = 110;

    public static final short NL80211_ATTR_MAX_REMAIN_ON_CHANNEL_DURATION = 111;

    public static final short NL80211_ATTR_MESH_SETUP = 112;

    public static final short NL80211_ATTR_WIPHY_ANTENNA_AVAIL_TX = 113;
    public static final short NL80211_ATTR_WIPHY_ANTENNA_AVAIL_RX = 114;

    public static final short NL80211_ATTR_SUPPORT_MESH_AUTH = 115;
    public static final short NL80211_ATTR_STA_PLINK_STATE = 116;

    public static final short NL80211_ATTR_WOWLAN_TRIGGERS = 117;
    public static final short NL80211_ATTR_WOWLAN_TRIGGERS_SUPPORTED = 118;

    public static final short NL80211_ATTR_SCHED_SCAN_INTERVAL = 119;

    public static final short NL80211_ATTR_INTERFACE_COMBINATIONS = 120;
    public static final short NL80211_ATTR_SOFTWARE_IFTYPES = 121;

    public static final short NL80211_ATTR_REKEY_DATA = 122;

    public static final short NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS = 123;
    public static final short NL80211_ATTR_MAX_SCHED_SCAN_IE_LEN = 124;

    public static final short NL80211_ATTR_SCAN_SUPP_RATES = 125;

    public static final short NL80211_ATTR_HIDDEN_SSID = 126;

    public static final short NL80211_ATTR_IE_PROBE_RESP = 127;
    public static final short NL80211_ATTR_IE_ASSOC_RESP = 128;

    public static final short NL80211_ATTR_STA_WME = 129;
    public static final short NL80211_ATTR_SUPPORT_AP_UAPSD = 130;

    public static final short NL80211_ATTR_ROAM_SUPPORT = 131;

    public static final short NL80211_ATTR_SCHED_SCAN_MATCH = 132;
    public static final short NL80211_ATTR_MAX_MATCH_SETS = 133;

    public static final short NL80211_ATTR_PMKSA_CANDIDATE = 134;

    public static final short NL80211_ATTR_TX_NO_CCK_RATE = 135;

    public static final short NL80211_ATTR_TDLS_ACTION = 136;
    public static final short NL80211_ATTR_TDLS_DIALOG_TOKEN = 137;
    public static final short NL80211_ATTR_TDLS_OPERATION = 138;
    public static final short NL80211_ATTR_TDLS_SUPPORT = 139;
    public static final short NL80211_ATTR_TDLS_EXTERNAL_SETUP = 140;

    public static final short NL80211_ATTR_DEVICE_AP_SME = 141;

    public static final short NL80211_ATTR_DONT_WAIT_FOR_ACK = 142;

    public static final short NL80211_ATTR_FEATURE_FLAGS = 143;

    public static final short NL80211_ATTR_PROBE_RESP_OFFLOAD = 144;

    public static final short NL80211_ATTR_PROBE_RESP = 145;

    public static final short NL80211_ATTR_DFS_REGION = 146;

    public static final short NL80211_ATTR_DISABLE_HT = 147;
    public static final short NL80211_ATTR_HT_CAPABILITY_MASK = 148;

    public static final short NL80211_ATTR_NOACK_MAP = 149;

    public static final short NL80211_ATTR_INACTIVITY_TIMEOUT = 150;

    public static final short NL80211_ATTR_RX_SIGNAL_DBM = 151;

    public static final short NL80211_ATTR_BG_SCAN_PERIOD = 152;

    public static final short NL80211_ATTR_WDEV = 153;

    public static final short NL80211_ATTR_USER_REG_HINT_TYPE = 154;

    public static final short NL80211_ATTR_CONN_FAILED_REASON = 155;

    public static final short NL80211_ATTR_AUTH_DATA = 156;

    public static final short NL80211_ATTR_VHT_CAPABILITY = 157;

    public static final short NL80211_ATTR_SCAN_FLAGS = 158;

    public static final short NL80211_ATTR_CHANNEL_WIDTH = 159;
    public static final short NL80211_ATTR_CENTER_FREQ1 = 160;
    public static final short NL80211_ATTR_CENTER_FREQ2 = 161;

    public static final short NL80211_ATTR_P2P_CTWINDOW = 162;
    public static final short NL80211_ATTR_P2P_OPPPS = 163;

    public static final short NL80211_ATTR_LOCAL_MESH_POWER_MODE = 164;

    public static final short NL80211_ATTR_ACL_POLICY = 165;

    public static final short NL80211_ATTR_MAC_ADDRS = 166;

    public static final short NL80211_ATTR_MAC_ACL_MAX = 167;

    public static final short NL80211_ATTR_RADAR_EVENT = 168;

    public static final short NL80211_ATTR_EXT_CAPA = 169;
    public static final short NL80211_ATTR_EXT_CAPA_MASK = 170;

    public static final short NL80211_ATTR_STA_CAPABILITY = 171;
    public static final short NL80211_ATTR_STA_EXT_CAPABILITY = 172;

    public static final short NL80211_ATTR_PROTOCOL_FEATURES = 173;
    public static final short NL80211_ATTR_SPLIT_WIPHY_DUMP = 174;

    public static final short NL80211_ATTR_DISABLE_VHT = 175;
    public static final short NL80211_ATTR_VHT_CAPABILITY_MASK = 176;

    public static final short NL80211_ATTR_MDID = 177;
    public static final short NL80211_ATTR_IE_RIC = 178;

    public static final short NL80211_ATTR_CRIT_PROT_ID = 179;
    public static final short NL80211_ATTR_MAX_CRIT_PROT_DURATION = 180;

    public static final short NL80211_ATTR_PEER_AID = 181;

    public static final short NL80211_ATTR_COALESCE_RULE = 182;

    public static final short NL80211_ATTR_CH_SWITCH_COUNT = 183;
    public static final short NL80211_ATTR_CH_SWITCH_BLOCK_TX = 184;
    public static final short NL80211_ATTR_CSA_IES = 185;
    public static final short NL80211_ATTR_CNTDWN_OFFS_BEACON = 186;
    public static final short NL80211_ATTR_CNTDWN_OFFS_PRESP = 187;

    public static final short NL80211_ATTR_RXMGMT_FLAGS = 188;

    public static final short NL80211_ATTR_STA_SUPPORTED_CHANNELS = 189;

    public static final short NL80211_ATTR_STA_SUPPORTED_OPER_CLASSES = 190;

    public static final short NL80211_ATTR_HANDLE_DFS = 191;

    public static final short NL80211_ATTR_SUPPORT_5_MHZ = 192;
    public static final short NL80211_ATTR_SUPPORT_10_MHZ = 193;

    public static final short NL80211_ATTR_OPMODE_NOTIF = 194;

    public static final short NL80211_ATTR_VENDOR_ID = 195;
    public static final short NL80211_ATTR_VENDOR_SUBCMD = 196;
    public static final short NL80211_ATTR_VENDOR_DATA = 197;
    public static final short NL80211_ATTR_VENDOR_EVENTS = 198;

    public static final short NL80211_ATTR_QOS_MAP = 199;

    public static final short NL80211_ATTR_MAC_HINT = 200;
    public static final short NL80211_ATTR_WIPHY_FREQ_HINT = 201;

    public static final short NL80211_ATTR_MAX_AP_ASSOC_STA = 202;

    public static final short NL80211_ATTR_TDLS_PEER_CAPABILITY = 203;

    public static final short NL80211_ATTR_SOCKET_OWNER = 204;

    public static final short NL80211_ATTR_CSA_C_OFFSETS_TX = 205;
    public static final short NL80211_ATTR_MAX_CSA_COUNTERS = 206;

    public static final short NL80211_ATTR_TDLS_INITIATOR = 207;

    public static final short NL80211_ATTR_USE_RRM = 208;

    public static final short NL80211_ATTR_WIPHY_DYN_ACK = 209;

    public static final short NL80211_ATTR_TSID = 210;
    public static final short NL80211_ATTR_USER_PRIO = 211;
    public static final short NL80211_ATTR_ADMITTED_TIME = 212;

    public static final short NL80211_ATTR_SMPS_MODE = 213;

    public static final short NL80211_ATTR_OPER_CLASS = 214;

    public static final short NL80211_ATTR_MAC_MASK = 215;

    public static final short NL80211_ATTR_WIPHY_SELF_MANAGED_REG = 216;

    public static final short NL80211_ATTR_EXT_FEATURES = 217;

    public static final short NL80211_ATTR_SURVEY_RADIO_STATS = 218;

    public static final short NL80211_ATTR_NETNS_FD = 219;

    public static final short NL80211_ATTR_SCHED_SCAN_DELAY = 220;

    public static final short NL80211_ATTR_REG_INDOOR = 221;

    public static final short NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS = 222;
    public static final short NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL = 223;
    public static final short NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS = 224;
    public static final short NL80211_ATTR_SCHED_SCAN_PLANS = 225;

    public static final short NL80211_ATTR_PBSS = 226;

    public static final short NL80211_ATTR_BSS_SELECT = 227;

    public static final short NL80211_ATTR_STA_SUPPORT_P2P_PS = 228;

    public static final short NL80211_ATTR_PAD = 229;

    public static final short NL80211_ATTR_IFTYPE_EXT_CAPA = 230;

    public static final short NL80211_ATTR_MU_MIMO_GROUP_DATA = 231;
    public static final short NL80211_ATTR_MU_MIMO_FOLLOW_MAC_ADDR = 232;

    public static final short NL80211_ATTR_SCAN_START_TIME_TSF = 233;
    public static final short NL80211_ATTR_SCAN_START_TIME_TSF_BSSID = 234;
    public static final short NL80211_ATTR_MEASUREMENT_DURATION = 235;
    public static final short NL80211_ATTR_MEASUREMENT_DURATION_MANDATORY = 236;

    public static final short NL80211_ATTR_MESH_PEER_AID = 237;

    public static final short NL80211_ATTR_NAN_MASTER_PREF = 238;
    public static final short NL80211_ATTR_BANDS = 239;
    public static final short NL80211_ATTR_NAN_FUNC = 240;
    public static final short NL80211_ATTR_NAN_MATCH = 241;

    public static final short NL80211_ATTR_FILS_KEK = 242;
    public static final short NL80211_ATTR_FILS_NONCES = 243;

    public static final short NL80211_ATTR_MULTICAST_TO_UNICAST_ENABLED = 244;

    public static final short NL80211_ATTR_BSSID = 245;

    public static final short NL80211_ATTR_SCHED_SCAN_RELATIVE_RSSI = 246;
    public static final short NL80211_ATTR_SCHED_SCAN_RSSI_ADJUST = 247;

    public static final short NL80211_ATTR_TIMEOUT_REASON = 248;

    public static final short NL80211_ATTR_FILS_ERP_USERNAME = 249;
    public static final short NL80211_ATTR_FILS_ERP_REALM = 250;
    public static final short NL80211_ATTR_FILS_ERP_NEXT_SEQ_NUM = 251;
    public static final short NL80211_ATTR_FILS_ERP_RRK = 252;
    public static final short NL80211_ATTR_FILS_CACHE_ID = 253;

    public static final short NL80211_ATTR_PMK = 254;

    public static final short NL80211_ATTR_SCHED_SCAN_MULTI = 255;
    public static final short NL80211_ATTR_SCHED_SCAN_MAX_REQS = 256;

    public static final short NL80211_ATTR_WANT_1X_4WAY_HS = 257;
    public static final short NL80211_ATTR_PMKR0_NAME = 258;
    public static final short NL80211_ATTR_PORT_AUTHORIZED = 259;

    public static final short NL80211_ATTR_EXTERNAL_AUTH_ACTION = 260;
    public static final short NL80211_ATTR_EXTERNAL_AUTH_SUPPORT = 261;

    public static final short NL80211_ATTR_NSS = 262;
    public static final short NL80211_ATTR_ACK_SIGNAL = 263;

    public static final short NL80211_ATTR_CONTROL_PORT_OVER_NL80211 = 264;

    public static final short NL80211_ATTR_TXQ_STATS = 265;
    public static final short NL80211_ATTR_TXQ_LIMIT = 266;
    public static final short NL80211_ATTR_TXQ_MEMORY_LIMIT = 267;
    public static final short NL80211_ATTR_TXQ_QUANTUM = 268;

    public static final short NL80211_ATTR_HE_CAPABILITY = 269;

    public static final short NL80211_ATTR_FTM_RESPONDER = 270;

    public static final short NL80211_ATTR_FTM_RESPONDER_STATS = 271;

    public static final short NL80211_ATTR_TIMEOUT = 272;

    public static final short NL80211_ATTR_PEER_MEASUREMENTS = 273;

    public static final short NL80211_ATTR_AIRTIME_WEIGHT = 274;
    public static final short NL80211_ATTR_STA_TX_POWER_SETTING = 275;
    public static final short NL80211_ATTR_STA_TX_POWER = 276;

    public static final short NL80211_ATTR_SAE_PASSWORD = 277;

    public static final short NL80211_ATTR_TWT_RESPONDER = 278;

    public static final short NL80211_ATTR_HE_OBSS_PD = 279;

    public static final short NL80211_ATTR_WIPHY_EDMG_CHANNELS = 280;
    public static final short NL80211_ATTR_WIPHY_EDMG_BW_CONFIG = 281;

    public static final short NL80211_ATTR_VLAN_ID = 282;

    public static final short NL80211_ATTR_HE_BSS_COLOR = 283;

    public static final short NL80211_ATTR_IFTYPE_AKM_SUITES = 284;

    public static final short NL80211_ATTR_TID_CONFIG = 285;

    public static final short NL80211_ATTR_CONTROL_PORT_NO_PREAUTH = 286;

    public static final short NL80211_ATTR_PMK_LIFETIME = 287;
    public static final short NL80211_ATTR_PMK_REAUTH_THRESHOLD = 288;

    public static final short NL80211_ATTR_RECEIVE_MULTICAST = 289;
    public static final short NL80211_ATTR_WIPHY_FREQ_OFFSET = 290;
    public static final short NL80211_ATTR_CENTER_FREQ1_OFFSET = 291;
    public static final short NL80211_ATTR_SCAN_FREQ_KHZ = 292;

    public static final short NL80211_ATTR_HE_6GHZ_CAPABILITY = 293;

    public static final short NL80211_ATTR_FILS_DISCOVERY = 294;

    public static final short NL80211_ATTR_UNSOL_BCAST_PROBE_RESP = 295;

    public static final short NL80211_ATTR_S1G_CAPABILITY = 296;
    public static final short NL80211_ATTR_S1G_CAPABILITY_MASK = 297;

    public static final short NL80211_ATTR_SAE_PWE = 298;

    public static final short NL80211_ATTR_RECONNECT_REQUESTED = 299;

    public static final short NL80211_ATTR_SAR_SPEC = 300;

    public static final short NL80211_ATTR_DISABLE_HE = 301;

    public static final short NL80211_ATTR_OBSS_COLOR_BITMAP = 302;

    public static final short NL80211_ATTR_COLOR_CHANGE_COUNT = 303;
    public static final short NL80211_ATTR_COLOR_CHANGE_COLOR = 304;
    public static final short NL80211_ATTR_COLOR_CHANGE_ELEMS = 305;

    public static final short NL80211_ATTR_MBSSID_CONFIG = 306;
    public static final short NL80211_ATTR_MBSSID_ELEMS = 307;

    public static final short NL80211_ATTR_RADAR_BACKGROUND = 308;

    public static final short NL80211_ATTR_AP_SETTINGS_FLAGS = 309;

    public static final short NL80211_ATTR_EHT_CAPABILITY = 310;

    public static final short NL80211_ATTR_DISABLE_EHT = 311;

    public static final short NL80211_ATTR_MLO_LINKS = 312;
    public static final short NL80211_ATTR_MLO_LINK_ID = 313;
    public static final short NL80211_ATTR_MLD_ADDR = 314;

    public static final short NL80211_ATTR_MLO_SUPPORT = 315;

    public static final short NL80211_ATTR_MAX_NUM_AKM_SUITES = 316;

    public static final short NL80211_ATTR_EML_CAPABILITY = 317;
    public static final short NL80211_ATTR_MLD_CAPA_AND_OPS = 318;

    public static final short NL80211_ATTR_TX_HW_TIMESTAMP = 319;
    public static final short NL80211_ATTR_RX_HW_TIMESTAMP = 320;
    public static final short NL80211_ATTR_TD_BITMAP = 321;

    public static final short NL80211_ATTR_PUNCT_BITMAP = 322;

    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_1 = 323;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_2 = 324;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_3 = 325;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_4 = 326;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_5 = 327;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_6 = 328;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_7 = 329;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_8 = 330;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_9 = 331;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_10 = 332;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_11 = 333;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_12 = 334;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_13 = 335;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_14 = 336;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_15 = 337;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_16 = 338;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_17 = 339;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_18 = 340;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_19 = 341;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_20 = 342;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_21 = 343;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_22 = 344;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_23 = 345;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_24 = 346;
    public static final short NL80211_ATTR_ANDROID_KABI_RESERVED_25 = 347;

    // Nl80211 frequency band values. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_BAND_2GHZ = 0;
    public static final short NL80211_BAND_5GHZ = 1;
    public static final short NL80211_BAND_60GHZ = 2;
    public static final short NL80211_BAND_6GHZ = 3;
    public static final short NL80211_BAND_S1GHZ = 4;
    public static final short NL80211_BAND_LC = 5;

    // Nl80211 interface type data attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_BAND_IFTYPE_ATTR_INVALID = 0;

    public static final short NL80211_BAND_IFTYPE_ATTR_IFTYPES = 1;
    public static final short NL80211_BAND_IFTYPE_ATTR_HE_CAP_MAC = 2;
    public static final short NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY = 3;
    public static final short NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET = 4;
    public static final short NL80211_BAND_IFTYPE_ATTR_HE_CAP_PPE = 5;
    public static final short NL80211_BAND_IFTYPE_ATTR_HE_6GHZ_CAPA = 6;
    public static final short NL80211_BAND_IFTYPE_ATTR_VENDOR_ELEMS = 7;
    public static final short NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MAC = 8;
    public static final short NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY = 9;
    public static final short NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET = 10;
    public static final short NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PPE = 11;
    public static final short NL80211_BAND_IFTYPE_ATTR_ANDROID_KABI_RESERVED_1 = 12;
    public static final short NL80211_BAND_IFTYPE_ATTR_ANDROID_KABI_RESERVED_2 = 13;
    public static final short NL80211_BAND_IFTYPE_ATTR_ANDROID_KABI_RESERVED_3 = 14;
    public static final short NL80211_BAND_IFTYPE_ATTR_ANDROID_KABI_RESERVED_4 = 15;
    public static final short NL80211_BAND_IFTYPE_ATTR_ANDROID_KABI_RESERVED_5 = 16;

    // Nl80211 band attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_BAND_ATTR_INVALID = 0;
    public static final short NL80211_BAND_ATTR_FREQS = 1;
    public static final short NL80211_BAND_ATTR_RATES = 2;

    public static final short NL80211_BAND_ATTR_HT_MCS_SET = 3;
    public static final short NL80211_BAND_ATTR_HT_CAPA = 4;
    public static final short NL80211_BAND_ATTR_HT_AMPDU_FACTOR = 5;
    public static final short NL80211_BAND_ATTR_HT_AMPDU_DENSITY = 6;

    public static final short NL80211_BAND_ATTR_VHT_MCS_SET = 7;
    public static final short NL80211_BAND_ATTR_VHT_CAPA = 8;
    public static final short NL80211_BAND_ATTR_IFTYPE_DATA = 9;

    public static final short NL80211_BAND_ATTR_EDMG_CHANNELS = 10;
    public static final short NL80211_BAND_ATTR_EDMG_BW_CONFIG = 11;
    public static final short NL80211_BAND_ATTR_ANDROID_KABI_RESERVED_1 = 12;
    public static final short NL80211_BAND_ATTR_ANDROID_KABI_RESERVED_2 = 13;
    public static final short NL80211_BAND_ATTR_ANDROID_KABI_RESERVED_3 = 14;
    public static final short NL80211_BAND_ATTR_ANDROID_KABI_RESERVED_4 = 15;
    public static final short NL80211_BAND_ATTR_ANDROID_KABI_RESERVED_5 = 16;

    public static final short NL80211_EXT_FEATURE_VHT_IBSS = 0;
    public static final short NL80211_EXT_FEATURE_RRM = 1;
    public static final short NL80211_EXT_FEATURE_MU_MIMO_AIR_SNIFFER = 2;
    public static final short NL80211_EXT_FEATURE_SCAN_START_TIME = 3;
    public static final short NL80211_EXT_FEATURE_BSS_PARENT_TSF = 4;
    public static final short NL80211_EXT_FEATURE_SET_SCAN_DWELL = 5;
    public static final short NL80211_EXT_FEATURE_BEACON_RATE_LEGACY = 6;
    public static final short NL80211_EXT_FEATURE_BEACON_RATE_HT = 7;
    public static final short NL80211_EXT_FEATURE_BEACON_RATE_VHT = 8;
    public static final short NL80211_EXT_FEATURE_FILS_STA = 9;
    public static final short NL80211_EXT_FEATURE_MGMT_TX_RANDOM_TA = 10;
    public static final short NL80211_EXT_FEATURE_MGMT_TX_RANDOM_TA_CONNECTED = 11;
    public static final short NL80211_EXT_FEATURE_SCHED_SCAN_RELATIVE_RSSI = 12;
    public static final short NL80211_EXT_FEATURE_CQM_RSSI_LIST = 13;
    public static final short NL80211_EXT_FEATURE_FILS_SK_OFFLOAD = 14;
    public static final short NL80211_EXT_FEATURE_4WAY_HANDSHAKE_STA_PSK = 15;
    public static final short NL80211_EXT_FEATURE_4WAY_HANDSHAKE_STA_1X = 16;
    public static final short NL80211_EXT_FEATURE_FILS_MAX_CHANNEL_TIME = 17;
    public static final short NL80211_EXT_FEATURE_ACCEPT_BCAST_PROBE_RESP = 18;
    public static final short NL80211_EXT_FEATURE_OCE_PROBE_REQ_HIGH_TX_RATE = 19;
    public static final short NL80211_EXT_FEATURE_OCE_PROBE_REQ_DEFERRAL_SUPPRESSION = 20;
    public static final short NL80211_EXT_FEATURE_MFP_OPTIONAL = 21;
    public static final short NL80211_EXT_FEATURE_LOW_SPAN_SCAN = 22;
    public static final short NL80211_EXT_FEATURE_LOW_POWER_SCAN = 23;
    public static final short NL80211_EXT_FEATURE_HIGH_ACCURACY_SCAN = 24;
    public static final short NL80211_EXT_FEATURE_DFS_OFFLOAD = 25;
    public static final short NL80211_EXT_FEATURE_CONTROL_PORT_OVER_NL80211 = 26;
    public static final short NL80211_EXT_FEATURE_ACK_SIGNAL_SUPPORT = 27;
    public static final short NL80211_EXT_FEATURE_TXQS = 28;
    public static final short NL80211_EXT_FEATURE_SCAN_RANDOM_SN = 29;
    public static final short NL80211_EXT_FEATURE_SCAN_MIN_PREQ_CONTENT = 30;
    public static final short NL80211_EXT_FEATURE_CAN_REPLACE_PTK0 = 31;
    public static final short NL80211_EXT_FEATURE_ENABLE_FTM_RESPONDER = 32;
    public static final short NL80211_EXT_FEATURE_AIRTIME_FAIRNESS = 33;
    public static final short NL80211_EXT_FEATURE_AP_PMKSA_CACHING = 34;
    public static final short NL80211_EXT_FEATURE_SCHED_SCAN_BAND_SPECIFIC_RSSI_THOLD = 35;
    public static final short NL80211_EXT_FEATURE_EXT_KEY_ID = 36;
    public static final short NL80211_EXT_FEATURE_STA_TX_PWR = 37;
    public static final short NL80211_EXT_FEATURE_SAE_OFFLOAD = 38;
    public static final short NL80211_EXT_FEATURE_VLAN_OFFLOAD = 39;
    public static final short NL80211_EXT_FEATURE_AQL = 40;
    public static final short NL80211_EXT_FEATURE_BEACON_PROTECTION = 41;
    public static final short NL80211_EXT_FEATURE_CONTROL_PORT_NO_PREAUTH = 42;
    public static final short NL80211_EXT_FEATURE_PROTECTED_TWT = 43;
    public static final short NL80211_EXT_FEATURE_DEL_IBSS_STA = 44;
    public static final short NL80211_EXT_FEATURE_MULTICAST_REGISTRATIONS = 45;
    public static final short NL80211_EXT_FEATURE_BEACON_PROTECTION_CLIENT = 46;
    public static final short NL80211_EXT_FEATURE_SCAN_FREQ_KHZ = 47;
    public static final short NL80211_EXT_FEATURE_CONTROL_PORT_OVER_NL80211_TX_STATUS = 48;
    public static final short NL80211_EXT_FEATURE_OPERATING_CHANNEL_VALIDATION = 49;
    public static final short NL80211_EXT_FEATURE_4WAY_HANDSHAKE_AP_PSK = 50;
    public static final short NL80211_EXT_FEATURE_SAE_OFFLOAD_AP = 51;
    public static final short NL80211_EXT_FEATURE_FILS_DISCOVERY = 52;
    public static final short NL80211_EXT_FEATURE_UNSOL_BCAST_PROBE_RESP = 53;
    public static final short NL80211_EXT_FEATURE_BEACON_RATE_HE = 54;
    public static final short NL80211_EXT_FEATURE_SECURE_LTF = 55;
    public static final short NL80211_EXT_FEATURE_SECURE_RTT = 56;
    public static final short NL80211_EXT_FEATURE_PROT_RANGE_NEGO_AND_MEASURE = 57;
    public static final short NL80211_EXT_FEATURE_BSS_COLOR = 58;
    public static final short NL80211_EXT_FEATURE_FILS_CRYPTO_OFFLOAD = 59;
    public static final short NL80211_EXT_FEATURE_RADAR_BACKGROUND = 60;
    public static final short NL80211_EXT_FEATURE_POWERED_ADDR_CHANGE = 61;
    public static final short NL80211_EXT_FEATURE_PUNCT = 62;
    public static final short NL80211_EXT_FEATURE_SECURE_NAN = 63;
    public static final short NL80211_EXT_FEATURE_AUTH_AND_DEAUTH_RANDOM_TA = 64;
    public static final short NL80211_EXT_FEATURE_OWE_OFFLOAD = 65;
    public static final short NL80211_EXT_FEATURE_OWE_OFFLOAD_AP = 66;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_3 = 67;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_4 = 68;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_5 = 69;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_6 = 70;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_7 = 71;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_8 = 72;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_9 = 73;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_10 = 74;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_11 = 75;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_12 = 76;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_13 = 77;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_14 = 78;
    public static final short NL80211_EXT_FEATURE_ANDROID_KABI_RESERVED_15 = 79;

    public static final short NL80211_FEATURE_SK_TX_STATUS = 0;
    public static final short NL80211_FEATURE_HT_IBSS = 1;
    public static final short NL80211_FEATURE_INACTIVITY_TIMER = 2;
    public static final short NL80211_FEATURE_CELL_BASE_REG_HINTS = 3;
    public static final short NL80211_FEATURE_P2P_DEVICE_NEEDS_CHANNEL = 4;
    public static final short NL80211_FEATURE_SAE = 5;
    public static final short NL80211_FEATURE_LOW_PRIORITY_SCAN = 6;
    public static final short NL80211_FEATURE_SCAN_FLUSH = 7;
    public static final short NL80211_FEATURE_AP_SCAN = 8;
    public static final short NL80211_FEATURE_VIF_TXPOWER = 9;
    public static final short NL80211_FEATURE_NEED_OBSS_SCAN = 10;
    public static final short NL80211_FEATURE_P2P_GO_CTWIN = 11;
    public static final short NL80211_FEATURE_P2P_GO_OPPPS = 12;
    public static final short NL80211_FEATURE_RESERVED_13 = 13;
    public static final short NL80211_FEATURE_ADVERTISE_CHAN_LIMITS = 14;
    public static final short NL80211_FEATURE_FULL_AP_CLIENT_STATE = 15;
    public static final short NL80211_FEATURE_USERSPACE_MPM = 16;
    public static final short NL80211_FEATURE_ACTIVE_MONITOR = 17;
    public static final short NL80211_FEATURE_AP_MODE_CHAN_WIDTH_CHANGE = 18;
    public static final short NL80211_FEATURE_DS_PARAM_SET_IE_IN_PROBES = 19;
    public static final short NL80211_FEATURE_WFA_TPC_IE_IN_PROBES = 20;
    public static final short NL80211_FEATURE_QUIET = 21;
    public static final short NL80211_FEATURE_TX_POWER_INSERTION = 22;
    public static final short NL80211_FEATURE_ACKTO_ESTIMATION = 23;
    public static final short NL80211_FEATURE_STATIC_SMPS = 24;
    public static final short NL80211_FEATURE_DYNAMIC_SMPS = 25;
    public static final short NL80211_FEATURE_SUPPORTS_WMM_ADMISSION = 26;
    public static final short NL80211_FEATURE_MAC_ON_CREATE = 27;
    public static final short NL80211_FEATURE_TDLS_CHANNEL_SWITCH = 28;
    public static final short NL80211_FEATURE_SCAN_RANDOM_MAC_ADDR = 29;
    public static final short NL80211_FEATURE_SCHED_SCAN_RANDOM_MAC_ADDR = 30;
    public static final short NL80211_FEATURE_ND_RANDOM_MAC_ADDR = 31;

    public static final short NL80211_DFS_USABLE = 0;
    public static final short NL80211_DFS_UNAVAILABLE = 1;
    public static final short NL80211_DFS_AVAILABLE = 2;

    public static final short NL80211_FREQUENCY_ATTR_INVALID = 0;
    public static final short NL80211_FREQUENCY_ATTR_FREQ = 1;
    public static final short NL80211_FREQUENCY_ATTR_DISABLED = 2;
    public static final short NL80211_FREQUENCY_ATTR_NO_IR = 3;
    public static final short NL80211_FREQUENCY_ATTR_NO_IBSS = 4;
    public static final short NL80211_FREQUENCY_ATTR_RADAR = 5;
    public static final short NL80211_FREQUENCY_ATTR_MAX_TX_POWER = 6;
    public static final short NL80211_FREQUENCY_ATTR_DFS_STATE = 7;
    public static final short NL80211_FREQUENCY_ATTR_DFS_TIME = 8;
    public static final short NL80211_FREQUENCY_ATTR_NO_HT40_MINUS = 9;
    public static final short NL80211_FREQUENCY_ATTR_NO_HT40_PLUS = 10;
    public static final short NL80211_FREQUENCY_ATTR_NO_80MHZ = 11;
    public static final short NL80211_FREQUENCY_ATTR_NO_160MHZ = 12;
    public static final short NL80211_FREQUENCY_ATTR_DFS_CAC_TIME = 13;
    public static final short NL80211_FREQUENCY_ATTR_INDOOR_ONLY = 14;
    public static final short NL80211_FREQUENCY_ATTR_IR_CONCURRENT = 15;
    public static final short NL80211_FREQUENCY_ATTR_NO_20MHZ = 16;
    public static final short NL80211_FREQUENCY_ATTR_NO_10MHZ = 17;
    public static final short NL80211_FREQUENCY_ATTR_WMM = 18;
    public static final short NL80211_FREQUENCY_ATTR_NO_HE = 19;
    public static final short NL80211_FREQUENCY_ATTR_OFFSET = 20;
    public static final short NL80211_FREQUENCY_ATTR_1MHZ = 21;
    public static final short NL80211_FREQUENCY_ATTR_2MHZ = 22;
    public static final short NL80211_FREQUENCY_ATTR_4MHZ = 23;
    public static final short NL80211_FREQUENCY_ATTR_8MHZ = 24;
    public static final short NL80211_FREQUENCY_ATTR_16MHZ = 25;
    public static final short NL80211_FREQUENCY_ATTR_NO_320MHZ = 26;
    public static final short NL80211_FREQUENCY_ATTR_NO_EHT = 27;

    // Nl80211 BSS attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_BSS_INVALID = 0;
    public static final short NL80211_BSS_BSSID = 1;
    public static final short NL80211_BSS_FREQUENCY = 2;
    public static final short NL80211_BSS_TSF = 3;
    public static final short NL80211_BSS_BEACON_INTERVAL = 4;
    public static final short NL80211_BSS_CAPABILITY = 5;
    public static final short NL80211_BSS_INFORMATION_ELEMENTS = 6;
    public static final short NL80211_BSS_SIGNAL_MBM = 7;
    public static final short NL80211_BSS_SIGNAL_UNSPEC = 8;
    public static final short NL80211_BSS_STATUS = 9;
    public static final short NL80211_BSS_SEEN_MS_AGO = 10;
    public static final short NL80211_BSS_BEACON_IES = 11;
    public static final short NL80211_BSS_CHAN_WIDTH = 12;
    public static final short NL80211_BSS_BEACON_TSF = 13;
    public static final short NL80211_BSS_PRESP_DATA = 14;
    public static final short NL80211_BSS_LAST_SEEN_BOOTTIME = 15;
    public static final short NL80211_BSS_PAD = 16;
    public static final short NL80211_BSS_PARENT_TSF = 17;
    public static final short NL80211_BSS_PARENT_BSSID = 18;
    public static final short NL80211_BSS_CHAIN_SIGNAL = 19;
    public static final short NL80211_BSS_FREQUENCY_OFFSET = 20;
    public static final short NL80211_BSS_MLO_LINK_ID = 21;
    public static final short NL80211_BSS_MLD_ADDR = 22;
    public static final short NL80211_BSS_ANDROID_KABI_RESERVED_1 = 23;
    public static final short NL80211_BSS_ANDROID_KABI_RESERVED_2 = 24;
    public static final short NL80211_BSS_ANDROID_KABI_RESERVED_3 = 25;

    // Nl80211 BSS Status values. See kernel/uapi/linux/nl80211.h
    public static final int NL80211_BSS_STATUS_AUTHENTICATED = 1;
    public static final int NL80211_BSS_STATUS_ASSOCIATED = 2;

    // Nl80211 scan flag values. See kernel/uapi/linux/nl80211.h
    public static final int NL80211_SCAN_FLAG_LOW_PRIORITY = 1 << 0;
    public static final int NL80211_SCAN_FLAG_FLUSH = 1 << 1;
    public static final int NL80211_SCAN_FLAG_AP = 1 << 2;
    public static final int NL80211_SCAN_FLAG_RANDOM_ADDR = 1 << 3;
    public static final int NL80211_SCAN_FLAG_FILS_MAX_CHANNEL_TIME = 1 << 4;
    public static final int NL80211_SCAN_FLAG_ACCEPT_BCAST_PROBE_RESP = 1 << 5;
    public static final int NL80211_SCAN_FLAG_OCE_PROBE_REQ_HIGH_TX_RATE = 1 << 6;
    public static final int NL80211_SCAN_FLAG_OCE_PROBE_REQ_DEFERRAL_SUPPRESSION = 1 << 7;
    public static final int NL80211_SCAN_FLAG_LOW_SPAN = 1 << 8;
    public static final int NL80211_SCAN_FLAG_LOW_POWER = 1 << 9;
    public static final int NL80211_SCAN_FLAG_HIGH_ACCURACY = 1 << 10;
    public static final int NL80211_SCAN_FLAG_RANDOM_SN = 1 << 11;
    public static final int NL80211_SCAN_FLAG_MIN_PREQ_CONTENT = 1 << 12;
    public static final int NL80211_SCAN_FLAG_FREQ_KHZ = 1 << 13;
    public static final int NL80211_SCAN_FLAG_COLOCATED_6GHZ = 1 << 14;
    public static final int NL80211_SCAN_FLAG_ANDROID_KABI_RESERVED_1 = 1 << 15;
    public static final int NL80211_SCAN_FLAG_ANDROID_KABI_RESERVED_2 = 1 << 16;
    public static final int NL80211_SCAN_FLAG_ANDROID_KABI_RESERVED_3 = 1 << 17;
    public static final int NL80211_SCAN_FLAG_ANDROID_KABI_RESERVED_4 = 1 << 18;
    public static final int NL80211_SCAN_FLAG_ANDROID_KABI_RESERVED_5 = 1 << 19;

    // Nl80211 scheduled scan match attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_INVALID = 0;
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_SSID = 1;
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_RSSI = 2;
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_RELATIVE_RSSI = 3;
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_RSSI_ADJUST = 4;
    public static final short NL80211_SCHED_SCAN_MATCH_ATTR_BSSID = 5;
    public static final short NL80211_SCHED_SCAN_MATCH_PER_BAND_RSSI = 6;

    // Nl80211 scheduled scan plan attributes. See kernel/uapi/linux/nl80211.h
    public static final short NL80211_SCHED_SCAN_PLAN_INVALID = 0;
    public static final short NL80211_SCHED_SCAN_PLAN_INTERVAL = 1;
    public static final short NL80211_SCHED_SCAN_PLAN_ITERATIONS = 2;
}

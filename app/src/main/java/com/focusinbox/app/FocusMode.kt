package com.focusinbox.app

enum class FocusMode { SINGLE, LOOP;
    companion object { fun fromStored(value: String?)=entries.firstOrNull { it.name==value } ?: SINGLE }
}

package com.example.facebookexcel.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facebook_items")
data class FacebookItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String = "",
    val title: String = "",
    val time: String = "",
    val image: String = ""
)

package com.stazis.subwaystations.model.entities

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Station(@PrimaryKey val name: String, val latitude: Double, val longitude: Double, val description: String) :
    Parcelable {

    companion object CREATOR : Parcelable.Creator<Station> {

        override fun createFromParcel(parcel: Parcel) = Station(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Station?>(size)
    }

    constructor() : this("", 0.0, 0.0, "")

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeString(description)
    }

    override fun describeContents() = 0
}
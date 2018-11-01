package com.stazis.subwaystations.model.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.stazis.subwaystations.helpers.ConnectionHelper
import com.stazis.subwaystations.helpers.PreferencesHelper
import com.stazis.subwaystations.model.entities.DetailedStation
import com.stazis.subwaystations.model.entities.Station
import com.stazis.subwaystations.model.entities.StationAdvancedInfo
import com.stazis.subwaystations.model.persistence.daos.DetailedStationDao
import com.stazis.subwaystations.model.services.StationService
import io.reactivex.Single
import io.reactivex.SingleEmitter
import org.jetbrains.anko.doAsync
import java.net.ConnectException

class RealStationRepository(
    private val stationService: StationService,
    private val detailedStationDao: DetailedStationDao,
    private val connectionHelper: ConnectionHelper,
    private val preferencesHelper: PreferencesHelper
) : StationRepository {

    companion object {

        private const val DATA_IN_FIRESTORE_KEY = "DATA_IN_FIRESTORE_KEY"
        private const val STATION_BASIC_INFO_COLLECTION_NAME = "StationBasicInfo"
        private const val STATION_DETAILED_INFO_COLLECTION_NAME = "StationDetailedInfo"
    }

    private val firestore = FirebaseFirestore.getInstance()

    override fun getStations(): Single<List<Station>> = if (connectionHelper.isOnline()) {
        loadStationsFromNetworks()
    } else {
        Single.create<List<Station>> { loadStationsFromDatabase(it) }
    }

    private fun loadStationsFromNetworks() = if (!preferencesHelper.retrieveBoolean(DATA_IN_FIRESTORE_KEY)) {
        preferencesHelper.saveBoolean(DATA_IN_FIRESTORE_KEY, true)
        loadStationsFromServer()
    } else {
        loadStationsFromFirestore()
    }

    private fun loadStationsFromServer() = stationService.getStations().doOnSuccess { stations ->
        ArrayList<Station>().apply {
            stations.forEach { ifCorrectCoordinates(it) { add(it) } }
            writeBasicStationsToFirestore(this)
            writeAdvancedStationsToFirestore(this)
            writeBasicStationInfoToDatabase(this)
        }
    }

    private fun ifCorrectCoordinates(station: Station, f: () -> Unit) {
        if (station.latitude != 0.0 && station.longitude != 0.0) {
            f()
        }
    }

    private fun writeBasicStationsToFirestore(stations: List<Station>) = stations.forEach {
        firestore.collection(STATION_BASIC_INFO_COLLECTION_NAME).document(it.name).set(it)
    }

    private fun writeAdvancedStationsToFirestore(stations: List<Station>) = stations.forEach {
        firestore.collection(STATION_DETAILED_INFO_COLLECTION_NAME).document(it.name).set(mapOf("description" to ""))
    }

    private fun writeBasicStationInfoToDatabase(stations: List<Station>) =
        detailedStationDao.insertAll(stations.map { DetailedStation(it.name, it.latitude, it.longitude, "") })

    private fun loadStationsFromFirestore() = Single.create<List<Station>> { emitter ->
        firestore.collection(STATION_BASIC_INFO_COLLECTION_NAME).get().addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result!!.isEmpty) {
                task.result!!.toObjects(Station::class.java).let { stations ->
                    emitter.onSuccess(ArrayList<Station>().also { correctStations ->
                        stations.forEach { ifCorrectCoordinates(it) { correctStations.add(it) } }
                        updateBasicStationsInDatabase(stations)
                    })
                }
            } else {
                emitter.onError(task.exception!!)
            }
        }
    }

    private fun updateBasicStationsInDatabase(stations: List<Station>) = doAsync {
        stations.map {
            DetailedStation(it.name, it.latitude, it.longitude, detailedStationDao.getDescription(it.name) ?: "")
        }
    }

    private fun loadStationsFromDatabase(emitter: SingleEmitter<List<Station>>) = detailedStationDao.getAll().let {
        if (!it.isEmpty()) {
            emitter.onSuccess(it)
        } else {
            emitter.onError(Exception("The database is empty!"))
        }
    }

    override fun getStationDescription(name: String): Single<String> = if (connectionHelper.isOnline()) {
        loadStationDescriptionFromFirestore(name)
    } else {
        Single.create<String> { loadStationDescriptionFromDatabase(it, name) }
    }

    private fun loadStationDescriptionFromFirestore(name: String) = Single.create<String> { emitter ->
        firestore.collection(STATION_DETAILED_INFO_COLLECTION_NAME).document(name).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                emitter.onSuccess(task.result!!.toObject(StationAdvancedInfo::class.java)!!.description)
            } else {
                emitter.onError(task.exception!!)
            }
        }
    }

    private fun loadStationDescriptionFromDatabase(emitter: SingleEmitter<String>, name: String) =
        detailedStationDao.getDescription(name).let {
            if (it != null) {
                emitter.onSuccess(it)
            } else {
                emitter.onError(Exception("No description found in the database!"))
            }
        }

    override fun updateStationDescription(name: String, description: String): Single<String> =
        Single.create { emitter ->
            if (connectionHelper.isOnline()) {
                firestore.collection(STATION_DETAILED_INFO_COLLECTION_NAME)
                    .document(name)
                    .update("description", description)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            emitter.onSuccess("Station updated successfully!")
                        } else {
                            emitter.onError(task.exception!!)
                        }
                    }
            } else {
                emitter.onError(ConnectException("No internet connection present!"))
            }
        }

    override fun updateLocalDatabase() {
        if (connectionHelper.isOnline()) {
            firestore.collection(STATION_BASIC_INFO_COLLECTION_NAME).get().continueWith { basicStationsTask ->
                firestore.collection(STATION_DETAILED_INFO_COLLECTION_NAME).get()
                    .addOnCompleteListener { advancedStationsTask ->
                        if (basicStationsTask.isSuccessful && advancedStationsTask.isSuccessful &&
                            !basicStationsTask.result!!.isEmpty && !advancedStationsTask.result!!.isEmpty
                        ) {
                            writeDetailedStationsToDatabase(basicStationsTask.result!!.toObjects(Station::class.java),
                                advancedStationsTask.result!!.map {
                                    it.id to it.toObject(StationAdvancedInfo::class.java)
                                })
                            Log.i("StationRepository", "Data updated successfully!")
                        } else {
                            Log.i("StationRepository", "Data update failed!")
                        }
                    }
            }
        }
    }

    private fun writeDetailedStationsToDatabase(
        basicStations: List<Station>,
        advancedStations: List<Pair<String, StationAdvancedInfo>>
    ) = doAsync {
        advancedStations.let {
            detailedStationDao.insertAll(basicStations.map { (name, latitude, longitude) ->
                DetailedStation(
                    name,
                    latitude,
                    longitude,
                    it.find { (first) -> first == name }!!.second.description
                )
            })
        }
    }
}
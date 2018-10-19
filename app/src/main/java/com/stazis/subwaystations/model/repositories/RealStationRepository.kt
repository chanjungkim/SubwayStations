package com.stazis.subwaystations.model.repositories

import com.stazis.subwaystations.helpers.ConnectionHelper
import com.stazis.subwaystations.model.entities.Station
import com.stazis.subwaystations.model.persistence.daos.StationDao
import com.stazis.subwaystations.model.services.StationService
import io.reactivex.Single
import io.reactivex.SingleEmitter

class RealStationRepository(
    private val stationService: StationService,
    private val stationDao: StationDao,
    private val connectionHelper: ConnectionHelper
) :
    StationRepository {

    override fun getStations(): Single<List<Station>> {
        return Single.create<List<Station>> { emitter: SingleEmitter<List<Station>> ->
            if (connectionHelper.isOnline()) {
                loadStationsFromNetwork(emitter)
            } else {
                loadStationsFromDatabase(emitter)
            }
        }
    }

    private fun loadStationsFromNetwork(emitter: SingleEmitter<List<Station>>) {
        try {
            val stations = stationService.getStations().execute().body()
            if (stations != null) {
                stationDao.insertAll(stations)
                emitter.onSuccess(stations)
            } else {
                emitter.onError(Exception("No data received!"))
            }
        } catch (exception: Exception) {
            emitter.onError(exception)
        }
    }

    private fun loadStationsFromDatabase(emitter: SingleEmitter<List<Station>>) {
        val stations = stationDao.getAll()
        if (!stations.isEmpty()) {
            emitter.onSuccess(stations)
        } else {
            emitter.onError(Exception("Database is empty!"))
        }
    }
}
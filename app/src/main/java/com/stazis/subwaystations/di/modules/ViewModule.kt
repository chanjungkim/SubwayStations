package com.stazis.subwaystations.di.modules

import com.stazis.subwaystations.view.general.StationListFragment
import com.stazis.subwaystations.view.general.StationMapFragment
import com.stazis.subwaystations.view.info.StationInfoActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ViewModule {

    @ContributesAndroidInjector
    abstract fun contributeStationListFragment(): StationListFragment

    @ContributesAndroidInjector
    abstract fun contributeStationMapFragment(): StationMapFragment

    @ContributesAndroidInjector
    abstract fun contributeStationInfoActivity(): StationInfoActivity
}
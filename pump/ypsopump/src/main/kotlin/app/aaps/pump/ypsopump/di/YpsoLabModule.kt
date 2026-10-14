package app.aaps.pump.ypsopump.di

import app.aaps.pump.ypsopump.YpsoLabFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class YpsoLabModule {
    @ContributesAndroidInjector
    abstract fun contributesYpsoLabFragment(): YpsoLabFragment
}

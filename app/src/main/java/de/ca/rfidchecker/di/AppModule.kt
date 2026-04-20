package de.ca.rfidchecker.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.ca.rfidchecker.data.local.AppDatabase
import de.ca.rfidchecker.data.local.MIGRATION_1_2
import de.ca.rfidchecker.data.local.MIGRATION_2_3
import de.ca.rfidchecker.data.local.MIGRATION_3_4
import de.ca.rfidchecker.data.local.MIGRATION_4_5
import de.ca.rfidchecker.data.local.MIGRATION_5_6
import de.ca.rfidchecker.data.repository.MismatchRepository
import de.ca.rfidchecker.device.honeywell.BarcodeScannerGateway
import de.ca.rfidchecker.device.honeywell.HoneywellCt37ScannerGateway
import de.ca.rfidchecker.device.zebra.RfidReaderGateway
import de.ca.rfidchecker.device.zebra.ZebraRfd8500Gateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ca-rfid-checker.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideMismatchRepository(db: AppDatabase): MismatchRepository =
        MismatchRepository(db)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {

    @Binds
    @Singleton
    abstract fun bindBarcodeScanner(impl: HoneywellCt37ScannerGateway): BarcodeScannerGateway

    @Binds
    @Singleton
    abstract fun bindRfidReader(impl: ZebraRfd8500Gateway): RfidReaderGateway
}
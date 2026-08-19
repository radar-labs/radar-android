package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Grandfathers already-registered installs into contact-discovery consent. Their address book was
 * synced by app versions that predate the prominent disclosure, and withdrawing discovery on update
 * would silently break their contact list. Migrations only run on app updates — fresh installs skip
 * them — so new users must still accept the disclosure before any address book data is collected.
 */
internal class ContactDiscoveryConsentMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(ContactDiscoveryConsentMigrationJob::class.java)
    const val KEY = "ContactDiscoveryConsentMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (SignalStore.account.isRegistered && !SignalStore.account.hasRecordedContactDiscoveryConsent) {
      Log.i(TAG, "Grandfathering an already-registered install into contact-discovery consent.")
      SignalStore.account.setContactDiscoveryConsent(true)
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ContactDiscoveryConsentMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ContactDiscoveryConsentMigrationJob {
      return ContactDiscoveryConsentMigrationJob(parameters)
    }
  }
}

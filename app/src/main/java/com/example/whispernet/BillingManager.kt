@file:Suppress("DEPRECATION")

package com.example.whispernet

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.core.content.edit
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams

class BillingManager(private val context: Context, private val onPremiumPurchased: () -> Unit) {
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val prefs =
                            context.getSharedPreferences("WhisperPrefs", Context.MODE_PRIVATE)
                        prefs.edit { putBoolean("isPremium", true) }
                        onPremiumPurchased()
                        Toast.makeText(context, "Premium unlocked!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        .enablePendingPurchases()
        .build()
    private var skuDetails: SkuDetails? = null

    init {

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySkuDetails()
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun querySkuDetails() {
        val skuList = listOf(context.getString(R.string.premium_sku))
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(skuList)
            .setType(BillingClient.SkuType.SUBS)
            .build()
        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                skuDetails = skuDetailsList.find { it.sku == context.getString(R.string.premium_sku) }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        skuDetails?.let {
            val flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(it)
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        } ?: Toast.makeText(context, "Premium not available yet", Toast.LENGTH_SHORT).show()
    }

    fun isPremium(): Boolean {
        val prefs = context.getSharedPreferences("WhisperPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("isPremium", false)
    }
}
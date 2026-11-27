package com.phonehunger.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow

data class ProState(val owned: Boolean = false, val price: String? = null, val connected: Boolean = false, val pending: Boolean = false, val error: String? = null, val lastVerifiedAt: Long = 0)

class BillingRepository(context: Context) : PurchasesUpdatedListener {
    private val cache = context.getSharedPreferences("play_entitlement_v1", Context.MODE_PRIVATE)
    val state = MutableStateFlow(ProState(owned = cache.getBoolean("owned", false), lastVerifiedAt = cache.getLong("verified_at", 0)))
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private var product: ProductDetails? = null
    private var connecting = false
    init { connect() }
    private fun connect() {
        if (connecting || client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    state.value = state.value.copy(connected = true, error = null)
                    refresh()
                } else state.value = state.value.copy(error = result.debugMessage)
            }
            override fun onBillingServiceDisconnected() { connecting = false; state.value = state.value.copy(connected = false) }
        })
    }
    fun refresh() {
        if (!client.isReady) { connect(); return }
        val query = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build()
        )).build()
        client.queryProductDetailsAsync(query) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = details.productDetailsList.firstOrNull { it.productId == PRODUCT }
                state.value = state.value.copy(price = product?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice)
            } else state.value = state.value.copy(error = result.debugMessage)
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) process(purchases)
            else state.value = state.value.copy(error = result.debugMessage)
        }
    }
    private fun process(purchases: List<Purchase>) {
        val owned = purchases.filter { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val timestamp = System.currentTimeMillis()
        cache.edit().putBoolean("owned", owned.isNotEmpty()).putLong("verified_at", timestamp).apply()
        state.value = state.value.copy(owned = owned.isNotEmpty(), pending = purchases.any { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }, lastVerifiedAt = timestamp)
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) state.value = state.value.copy(error = result.debugMessage)
            }
        }
    }
    fun buy(activity: Activity) {
        val details = product ?: return
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return
        val token = offer.offerToken ?: return
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(token).build()
        )).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) state.value = state.value.copy(error = result.debugMessage)
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) state.value = state.value.copy(error = result.debugMessage)
    }
    fun close() = client.endConnection()
    companion object { const val PRODUCT = "phonehunger_pro_lifetime" }
}

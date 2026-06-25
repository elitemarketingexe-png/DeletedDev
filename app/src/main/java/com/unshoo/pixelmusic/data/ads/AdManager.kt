package com.unshoo.pixelmusic.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.unshoo.pixelmusic.BuildConfig

object AdManager {
    private const val TAG = "AdManager"
    private const val AD_UNIT_ID = "ca-app-pub-623520458880294/664124376"
    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private const val PREFS_NAME = "ad_manager_prefs"
    private const val KEY_APP_OPEN_COUNT = "app_open_count"
    private const val KEY_LAST_POPUP_TIME = "last_popup_time"
    private const val KEY_POPUP_STATUS = "popup_status"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context.applicationContext) {}
            loadRewardedAd(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "AdMob initialization failed", e)
        }
    }

    private fun getAdUnitId(): String {
        return if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else AD_UNIT_ID
    }

    fun loadRewardedAd(context: Context) {
        try {
            if (rewardedAd != null || isLoading) return
            isLoading = true
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(context.applicationContext, getAdUnitId(), adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: ${adError.message}")
                    rewardedAd = null
                    isLoading = false
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad was loaded.")
                    rewardedAd = ad
                    isLoading = false
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load rewarded ad", e)
            isLoading = false
        }
    }

    fun isAdLoaded(): Boolean {
        return rewardedAd != null
    }

    fun showRewardedAd(activity: Activity, onAdCompleted: (Boolean) -> Unit) {
        try {
            val ad = rewardedAd
            if (ad != null) {
                var userEarnedReward = false
                ad.show(activity) { rewardItem ->
                    userEarnedReward = true
                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                }
                ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad dismissed fullscreen content.")
                        rewardedAd = null
                        // Load the next ad
                        loadRewardedAd(activity.applicationContext)
                        activity.runOnUiThread {
                            onAdCompleted(userEarnedReward)
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        Log.d(TAG, "Ad failed to show: ${adError.message}")
                        rewardedAd = null
                        loadRewardedAd(activity.applicationContext)
                        activity.runOnUiThread {
                            onAdCompleted(false)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Ad was not ready yet.")
                loadRewardedAd(activity.applicationContext)
                onAdCompleted(false)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to show rewarded ad", e)
            onAdCompleted(false)
        }
    }

    fun incrementAppOpenCount(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
            prefs.edit().putInt(KEY_APP_OPEN_COUNT, currentCount + 1).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to increment app open count", e)
        }
    }

    fun shouldShowSupportPopup(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val openCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
            if (openCount < 5) return false

            val lastPopupTime = prefs.getLong(KEY_LAST_POPUP_TIME, 0L)
            if (lastPopupTime == 0L) return true

            val popupStatus = prefs.getString(KEY_POPUP_STATUS, "none")
            val elapsedTime = System.currentTimeMillis() - lastPopupTime
            val daysRequired = if (popupStatus == "watched") 5 else 3
            val timeRequiredMs = daysRequired * 24L * 60L * 60L * 1000L
            
            return elapsedTime >= timeRequiredMs
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to check if support popup should be shown", e)
            return false
        }
    }

    fun recordPopupShown(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_POPUP_TIME, System.currentTimeMillis()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to record popup shown", e)
        }
    }

    fun recordPopupResponse(context: Context, watched: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val status = if (watched) "watched" else "dismissed"
            prefs.edit().putString(KEY_POPUP_STATUS, status).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to record popup response", e)
        }
    }
}

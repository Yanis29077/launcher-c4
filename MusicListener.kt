package com.launcherc4.home

import android.service.notification.NotificationListenerService

// Ce service ne fait rien lui-même : il sert uniquement à obtenir
// l'autorisation de lire la musique en cours (titre, artiste, pochette).
class MusicListener : NotificationListenerService()

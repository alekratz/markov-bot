package edu.appstate.cs.markovbot
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */

class NaiveTrustManager : X509TrustManager {
    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
        // do nothing
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate>? {
        return null
    }

    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
        // do nothing
    }

}

private var socketFactory: SSLSocketFactory? = null

fun getNaiveSocketFactory(): SSLSocketFactory {
    if(socketFactory == null) {
        val tm = Array<TrustManager>(1) { NaiveTrustManager() }
        val context = SSLContext.getInstance("SSL")
        context.init(arrayOf<KeyManager>(), tm, SecureRandom())
        socketFactory = context.socketFactory
    }

    return socketFactory!!
}
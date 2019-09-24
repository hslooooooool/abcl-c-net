package vip.qsos.core_file

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * @author : 华清松
 * 图片URI转File或Bitmap
 */
object RxImageConverters {

    /**转为File*/
    fun uriToFile(context: Context, uri: Uri, file: File?): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            file!!.copyInputStreamToFile(inputStream!!)
            file
        } catch (e: Exception) {
            null
        }
    }

    /**转为File*/
    fun uriToFileObservable(context: Context, uri: Uri, file: File?): Observable<File> {
        return Observable.create(ObservableOnSubscribe<File> { emitter ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                file!!.copyInputStreamToFile(inputStream!!)
                emitter.onNext(file)
                emitter.onComplete()
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    /**转为Bitmap*/
    fun uriToBitmap(context: Context, uri: Uri): Observable<Bitmap> {
        return Observable.create(ObservableOnSubscribe<Bitmap> { emitter ->
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                emitter.onNext(bitmap)
                emitter.onComplete()
            } catch (e: IOException) {
                emitter.onError(e)
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    private fun File.copyInputStreamToFile(inputStream: InputStream) {
        inputStream.use { input ->
            this.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }
    }

}

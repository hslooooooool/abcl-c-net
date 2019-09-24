package vip.qsos.core_file

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author : 华清松
 * 图片选择界面
 */
class RxImagePicker : Fragment() {

    /**观察是否绑定Activity*/
    private lateinit var attachedSubject: PublishSubject<Boolean>
    /**选择单张*/
    private lateinit var publishSubject: PublishSubject<Uri>
    /**选择多张*/
    private lateinit var publishMultipleSubject: PublishSubject<List<Uri>>
    /**选择取消*/
    private lateinit var canceledSubject: PublishSubject<Int>
    /**是否为多选*/
    private var isMultiple = false
    /**选择方式*/
    private var imageSource: Sources? = null
    /**选择界面标题 Sources.CHOOSER 时生效*/
    private var chooserTitle: String? = null

    /**单图选择*/
    fun requestImage(source: Sources, chooserTitle: String? = "图片选择"): Observable<Uri> {
        initSubjects()
        this.chooserTitle = chooserTitle
        isMultiple = false
        imageSource = source
        requestPickImage()
        return publishSubject.takeUntil(canceledSubject)
    }

    /**多图选择*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun requestMultipleImages(): Observable<List<Uri>> {
        initSubjects()
        imageSource = Sources.DOCUMENTS
        isMultiple = true
        requestPickImage()
        return publishMultipleSubject.takeUntil(canceledSubject)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 保留Fragment
        retainInstance = true
    }

    private fun initSubjects() {
        publishSubject = PublishSubject.create()
        attachedSubject = PublishSubject.create()
        canceledSubject = PublishSubject.create()
        publishMultipleSubject = PublishSubject.create()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (::attachedSubject.isInitialized.not() or
                ::publishSubject.isInitialized.not() or
                ::publishMultipleSubject.isInitialized.not() or
                ::canceledSubject.isInitialized.not()) {
            initSubjects()
        }
        attachedSubject.onNext(true)
        attachedSubject.onComplete()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickImage()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            /**选择结果回调*/
            when (requestCode) {
                SELECT_PHOTO -> handleGalleryResult(data)
                TAKE_PHOTO -> pushImage(cameraPictureUrl)
                CHOOSER -> if (isCamera(data)) pushImage(cameraPictureUrl) else handleGalleryResult(data)
            }
        } else {
            /**取消选择*/
            canceledSubject.onNext(requestCode)
        }
    }

    /**是否为拍照照片*/
    private fun isCamera(data: Intent?): Boolean {
        return data == null || data.data == null && data.clipData == null
    }

    /**获取图库选择结果*/
    private fun handleGalleryResult(data: Intent?) {
        if (isMultiple) {
            /**多图回传*/
            val imageUris = ArrayList<Uri>()
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    imageUris.add(clipData.getItemAt(i).uri)
                }
            } else {
                data?.data?.let { imageUris.add(it) }
            }
            pushImageList(imageUris)
        } else {
            /**单图回传*/
            pushImage(data?.data)
        }
    }

    /**开始图片选择*/
    @SuppressLint("CheckResult")
    private fun requestPickImage() {
        if (!isAdded) {
            // 未持有Activity时，绑定观察
            attachedSubject.subscribe {
                pickImage()
            }
        } else {
            pickImage()
        }
    }

    /**图片选取*/
    private fun pickImage() {
        if (!checkPermission()) {
            return
        }
        var chooseCode = 0
        var pictureChooseIntent: Intent? = null
        when (imageSource) {
            Sources.CAMERA -> {
                cameraPictureUrl = createImageUri()
                pictureChooseIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                pictureChooseIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPictureUrl)
                grantWritePermission(context!!, pictureChooseIntent, cameraPictureUrl!!)
                chooseCode = TAKE_PHOTO
            }
            Sources.GALLERY -> {
                pictureChooseIntent = createPickFromGalleryIntent()
                chooseCode = SELECT_PHOTO
            }
            Sources.DOCUMENTS -> {
                pictureChooseIntent = createPickFromDocumentsIntent()
                chooseCode = SELECT_PHOTO
            }
            Sources.CHOOSER -> {
                pictureChooseIntent = createChooserIntent(chooserTitle)
                chooseCode = CHOOSER
            }
        }

        startActivityForResult(pictureChooseIntent, chooseCode)
    }

    /**图片选择器*/
    private fun createChooserIntent(chooserTitle: String?): Intent {
        cameraPictureUrl = createImageUri()
        val cameraIntents = ArrayList<Intent>()
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val packageManager = context!!.packageManager
        val camList = packageManager.queryIntentActivities(captureIntent, 0)
        for (res in camList) {
            val packageName = res.activityInfo.packageName
            val intent = Intent(captureIntent)
            intent.component = ComponentName(res.activityInfo.packageName, res.activityInfo.name)
            intent.setPackage(packageName)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPictureUrl)
            grantWritePermission(context!!, intent, cameraPictureUrl!!)
            cameraIntents.add(intent)
        }
        val galleryIntent = createPickFromDocumentsIntent()
        val chooserIntent = Intent.createChooser(galleryIntent, chooserTitle)
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toTypedArray())
        return chooserIntent
    }

    /**构建图库选择Intent*/
    private fun createPickFromGalleryIntent(): Intent {
        val pictureChooseIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            pictureChooseIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple)
        }
        pictureChooseIntent.type = "image/*"
        return pictureChooseIntent
    }

    /**构建文档管理选择Intent*/
    private fun createPickFromDocumentsIntent(): Intent {
        val pictureChooseIntent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            pictureChooseIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            pictureChooseIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple)
            pictureChooseIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        } else {
            pictureChooseIntent = Intent(Intent.ACTION_GET_CONTENT)
        }
        pictureChooseIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        pictureChooseIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pictureChooseIntent.type = "image/*"
        return pictureChooseIntent
    }

    /**申请文件读写权限*/
    private fun checkPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            }
            false
        } else {
            true
        }
    }

    /**创建拍照保存路径*/
    private fun createImageUri(): Uri? {
        val contentResolver = activity!!.contentResolver
        val cv = ContentValues()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        cv.put(MediaStore.Images.Media.TITLE, timeStamp)
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
    }

    /**申请文件读写权限*/
    private fun grantWritePermission(context: Context, intent: Intent, uri: Uri) {
        val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**传递选择的多图*/
    private fun pushImageList(uris: List<Uri>) {
        publishMultipleSubject.onNext(uris)
        publishMultipleSubject.onComplete()
    }

    /**传递选择的单图*/
    private fun pushImage(uri: Uri?) {
        if (uri == null) {
            publishSubject.onError(Throwable(NullPointerException("Uri is null")))
        } else {
            publishSubject.onNext(uri)
        }
        publishSubject.onComplete()
    }

    companion object {

        private const val SELECT_PHOTO = 100
        private const val TAKE_PHOTO = 101
        private const val CHOOSER = 102

        private val TAG = RxImagePicker::class.java.simpleName
        /**拍照存储URI*/
        private var cameraPictureUrl: Uri? = null

        fun with(fm: FragmentManager): RxImagePicker {
            var rxImagePickerFragment = fm.findFragmentByTag(TAG) as RxImagePicker?
            if (rxImagePickerFragment == null) {
                rxImagePickerFragment = RxImagePicker()
                fm.beginTransaction().add(rxImagePickerFragment, TAG).commit()
            }
            return rxImagePickerFragment
        }
    }

}

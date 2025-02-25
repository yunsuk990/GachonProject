package com.example.fitpho.Aimovement

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitpho.Network.API
import com.example.fitpho.NetworkModel.AiMoveResponse
import com.example.fitpho.NetworkModel.getRetrofit
import com.example.fitpho.R
import com.example.fitpho.databinding.FragmentAiMovementChoiceBinding
import com.example.fitpho.ml.Model
import com.example.fitpho.util.SharedPreferenceUtil
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.nio.ByteBuffer
import java.nio.ByteOrder


class AiMovementChoiceFragment : Fragment() {


    private var _binding: FragmentAiMovementChoiceBinding? = null
    private val binding get() = _binding!!
    private val movementAdapter by lazy { MovementAdapter(requireContext()) }
    companion object{
        lateinit var prefs: SharedPreferenceUtil
    }
    var imageSize: Int = 224
    var image: Bitmap? = null
    var type: Int = 100
    var ID: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAiMovementChoiceBinding.inflate(inflater, container, false)
        var mytoolbar  = binding.toolbar
        (activity as AppCompatActivity).setSupportActionBar(mytoolbar)
        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            image = data?.extras?.get("data") as Bitmap
            var dimension: Int = Math.min(image!!.width, image!!.height)
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
            image = Bitmap.createScaledBitmap(image!!, imageSize, imageSize, false)
            classifyImage(image)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = SharedPreferenceUtil(requireContext())
        //구분선
        var dividerItemDecoration: DividerItemDecoration = DividerItemDecoration(view.context, 1)
        dividerItemDecoration.setDrawable(context?.resources!!.getDrawable(R.drawable.recyclerview_divider))
        binding.moveRecycle.addItemDecoration(dividerItemDecoration)

        binding.moveRecycle.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.moveRecycle.adapter = movementAdapter

        // 운동기구 리스트 나열
        //getMoveData()
        var list: ArrayList<String> = ArrayList()
        list.add("팔굽혀펴기")
        list.add("스쿼트")
        list.add("런지")
        list.add("엘보우 플랭크")
        list.add("사이드 플랭크")
        list.add("할로우 홀드")
        list.add("브릿지")
        list.add("팔 벌려 뛰기")

        movementAdapter.setitemList(list)

        movementAdapter.AimoveClickItem(object: MovementAdapter.AiMoveClickListener{

            override fun itemClick(title: String) {
                ID = title
                if (requireActivity().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    var cameraIntent: Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(cameraIntent, 1)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
                }
            }
        })

    }
    private fun classifyImage(image: Bitmap?) {

        val model = Model.newInstance(requireActivity().applicationContext)
// Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
        var byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*imageSize*imageSize*3)
        byteBuffer.order(ByteOrder.nativeOrder())

        var intValues = IntArray(imageSize*imageSize)
        image?.getPixels(intValues, 0, image.width, 0,0,image.width, image.height)
        var pixel:Int = 0
        for(i in 0 until imageSize){
            for(j in 0 until imageSize){
                val i: Int = intValues[pixel++] //RGB
                byteBuffer.putFloat( (((i.shr(16)))and((0xFF)))*(1.0f/ 255.0f))
                byteBuffer.putFloat((( i.shr(8))and(0xFF))*(1.0f/ 255.0f))
                byteBuffer.putFloat((i and (0xFF))*(1.0f/255.0f))
            }
        }
        inputFeature0.loadBuffer(byteBuffer)

// Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val confidences = outputFeature0.floatArray

        var maxconfidences = 0.0F
        var maxPos = 0

        for(i in 0 until confidences?.size!!){
            if(confidences[i] > maxconfidences){
                maxconfidences = confidences[i]
                maxPos = i
            }
        }

        val classes: Array<String> = arrayOf("런지 자세", "브릿지 자세", "사이드 플랭크 자세", "스쿼트 자세", "엘보우 플랭크 자세", "팔 벌려 뛰기 자세", "팔굽혀펴기 자세",
            "할로우 홀드 동작")

        var s: String? = ""
        Log.d("classSize", classes.size.toString())

        //모든 항목 예측치 출력
        for(i in 0 until classes.size){
            s+= String.format("%s: %.3f%%\n", classes[i], confidences[i]*100)
        }
        var act = confidences[maxPos]*100
        Log.d("Result", s!!) // 결과값 출력
        Log.d("매칭운동", classes[maxPos].toString())


        if(act>80){
           type = 0
        }else if(act>50){
            type = 1
        }
        else if(act>20){
            type = 2
        }else{
            Toast.makeText(requireContext(), "인식할 수 없습니다, 다시 촬영해주세요.", Toast.LENGTH_LONG).show()
        }

// Releases model resources if no longer used.
        model.close()

        findNavController().navigate(R.id.ai_movement_result, Bundle().apply {
            putString("type", "2")
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


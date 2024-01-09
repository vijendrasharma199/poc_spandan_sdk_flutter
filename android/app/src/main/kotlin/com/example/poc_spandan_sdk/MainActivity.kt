package com.example.poc_spandan_sdk

import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.lifecycle.MutableLiveData
import `in`.sunfox.healthcare.commons.android.spandan_sdk.OnInitializationCompleteListener
import `in`.sunfox.healthcare.commons.android.spandan_sdk.OnReportGenerationStateListener
import `in`.sunfox.healthcare.commons.android.spandan_sdk.SpandanSDK
import `in`.sunfox.healthcare.commons.android.spandan_sdk.collection.EcgTest
import `in`.sunfox.healthcare.commons.android.spandan_sdk.collection.EcgTestCallback
import `in`.sunfox.healthcare.commons.android.spandan_sdk.conclusion.EcgReport
import `in`.sunfox.healthcare.commons.android.spandan_sdk.connection.DeviceInfo
import `in`.sunfox.healthcare.commons.android.spandan_sdk.connection.OnDeviceConnectionStateChangeListener
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.DeviceConnectionState
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.EcgPosition
import `in`.sunfox.healthcare.commons.android.spandan_sdk.enums.EcgTestType
import `in`.sunfox.healthcare.java.commons.ecg_processor.conclusions.conclusion.LeadTwoConclusion
import `in`.sunfox.healthcare.java.commons.ecg_processor.conclusions.conclusion.TwelveLeadConclusion
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodChannel


class MainActivity : FlutterActivity() {
    private val TAG = "MainActivity.TAG"
    private val CHANNEL = "com.example.poc_spandan_sdk/sericom"
    private val CHANNEL_EVENT = "com.example.poc_spandan_sdk/sericom_event"

    //sdk variables
    lateinit var span: SpandanSDK
    lateinit var token: String

    private lateinit var ecgTest: EcgTest
    private lateinit var ecgTestType: EcgTestType
    val hashMap = HashMap<EcgPosition, ArrayList<Double>>()
    private lateinit var ecgReport: EcgReport
    private val ecgDataHash = hashMapOf<EcgPosition, ArrayList<Double>>()

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var deviceStatusData = MutableLiveData<String>()
    private var timerData = MutableLiveData<String>()
    private var resultData = MutableLiveData<String>()

    data class ShareLeadData(
        val resultString: String, val resultHashMapData: HashMap<String, ArrayList<Double>>
    )

    private var shareResultClass = MutableLiveData<ShareLeadData>()
    private var resultHashMap = HashMap<String, ArrayList<Double>>()


    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                /**
                 * Step 1. Setup the connection(Note : Call it once when app opens)
                 * **/
                "setUpConnection" -> {
                    setUpConnection(result)
                }

                /**
                 * Step 2. Create the test
                 * **/
                "createTest" -> {
                    when(call.argument<String>("testType").toString()){
                        "1" -> {
                            result.success("Lead two test created")
                            createTest(EcgTestType.LEAD_TWO)
                            resultData.value = "Lead two test created"
                        }
                        "2" -> {
                            result.success("Lead 12 test created")
                            createTest(EcgTestType.TWELVE_LEAD)
                            resultData.value = "Lead 12 test created"
                        }
                        else -> {
                            resultData.value = "Invalid operation..."
                            result.success("Invalid operation...")
                        }
                    }
                }

                /**
                 * Step 3. Perform the lead test according to testType
                 * **/
                "sendCommand" -> {
                    when(call.argument<String>("leadName").toString()){
                        "V1" -> ecgTest.start(EcgPosition.V1)
                        "V2" -> ecgTest.start(EcgPosition.V2)
                        "V3" -> ecgTest.start(EcgPosition.V3)
                        "V4" -> ecgTest.start(EcgPosition.V4)
                        "V5" -> ecgTest.start(EcgPosition.V5)
                        "V6" -> ecgTest.start(EcgPosition.V6)
                        "Lead_1" -> ecgTest.start(EcgPosition.LEAD_1)
                        "Lead_2" -> ecgTest.start(EcgPosition.LEAD_2)
                        else -> {
                            result.success(false)
                        }
                    }
                    result.success(true)
                }

                /**
                 * Step 4. Generate the report
                 * **/
                "generateReport" -> {
                    when(call.argument<String>("testType").toString()){
                        "1" -> {
                            resultData.value = "Generating report..."
                            generateReport(EcgTestType.LEAD_TWO, result)
                        }
                        "2" -> {
                            resultData.value = "Generating report..."
                            generateReport(EcgTestType.TWELVE_LEAD, result)
                        }
                        else -> {
                            result.success("Invalid operation...")
                        }
                    }
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        eventChannel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_EVENT)
        eventChannel.setStreamHandler(object : StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                //observe device response data
                resultData.observe(this@MainActivity) {
                    events!!.success(resultData.value)
                }
            }

            override fun onCancel(arguments: Any?) {

            }

        })

        Log.w(TAG, "configureFlutterEngine: Called End....")

    }

    private fun createTest(testType: EcgTestType){
        ecgTest = span.createTest(testType, object : EcgTestCallback {
            override fun onTestFailed(statusCode: Int) {
                Log.e(TAG, "onTestFailed: $statusCode")
                runOnUiThread {
                    resultData.value = "Failed with code $statusCode"
                    shareResultClass.value = ShareLeadData("Error-->Failed with code $statusCode", resultHashMap)
                }
            }

            override fun onTestStarted(ecgPosition: EcgPosition) {
                Log.d(TAG, "onTestStarted: EcgPosition -> $ecgPosition")
                runOnUiThread {
                    resultData.value = "Started : $ecgPosition"
                    shareResultClass.value = ShareLeadData("Started : ${ecgPosition.name}", resultHashMap)
                }
            }

            override fun onElapsedTimeChanged(elapsedTime: Long, remainingTime: Long) {
                //update only lead 2 progress bar
                runOnUiThread {
                    resultData.value = "Test Name : ${testType.name} Remaining ${(remainingTime - elapsedTime).toInt()} : from ${elapsedTime.toInt()}"
                }
            }

            override fun onReceivedData(data: String) {
                //Log.w(TAG, "onReceivedData: $data")
                runOnUiThread {
//                        resultData.value = "Data : $data"
                    //shareResultClass.value = ShareResultClass("Data : $data", resultHashMap)
                }
            }

            override fun onPositionRecordingComplete(
                ecgPosition: EcgPosition, ecgPoints: ArrayList<Double>?
            ) {
                Log.d(TAG, "onPositionRecordingComplete: EcgPosition --> $ecgPosition : EcgPoints --> ${ecgPoints!!.size}")

                //put all the ecgPoints in hashmap to generate report
                hashMap[ecgPosition] = ecgPoints
                resultData.value = "${ecgPosition.name} done."
            }
        }, token)
    }

    private fun generateReport(testType: EcgTestType, result: MethodChannel.Result) {
        if(token.isNotEmpty()){
            if(hashMap.size > 0){
                span.generateReport(32, hashMap, token,
                    object : OnReportGenerationStateListener {
                        override fun onReportGenerationSuccess(ecgReport: EcgReport) {
                            result.success("Report Generated...")

                            //generate report according to test wise

                            //If test is Lead-2
                            if (testType == EcgTestType.LEAD_TWO) {
                                val conclusion = ecgReport.conclusion as LeadTwoConclusion
                                val characteristics = ecgReport.ecgCharacteristics
                                Log.d(TAG, "onReportGenerationSuccess:  Conclusion --> $conclusion : Characteristics --> $characteristics")
                                runOnUiThread {
                                    resultData.value =
                                        "Report Data :::\nDetection : ${conclusion.detection}\n" +
                                                "EcgType : ${conclusion.ecgType}\n" +
                                                "BaseLine Wandering : ${conclusion.baselineWandering}\n" +
                                                "pWave Type : ${conclusion.pWaveType}\n" +
                                                "QRS Type : ${conclusion.qrsType}\n" +
                                                "PowerLine Interference : ${conclusion.powerLineInterference}"
                              }
                            }

                            //If test is Lead-12
                            if (testType == EcgTestType.TWELVE_LEAD) {
                                val conclusion = ecgReport.conclusion as TwelveLeadConclusion
                                val characteristics = ecgReport.ecgCharacteristics
                                Log.d(TAG, "onReportGenerationSuccess:  Conclusion --> $conclusion : Characteristics --> $characteristics")
                                runOnUiThread {
                                    resultData.value =
                                        "Report Data :::\nDetection : ${conclusion.detection}\n" +
                                                "EcgType : ${conclusion.ecgType}\n" +
                                                "Anomalies : ${conclusion.anomalies}\n" +
                                                "Risk : ${conclusion.risk}\n" +
                                                "Recommendation : ${conclusion.recommendation}"
                                }
                            }
                        }

                        override fun onReportGenerationFailed(errorCode: Int, errorMsg: String) {
                            result.success("Not able to generate report due to $errorMsg")
                            runOnUiThread {
                                resultData.value = "Not able to generate report due to $errorMsg"
                            }
                        }
                    })
            }
            else{
                result.success("Please perform the test first...")
            }
        }
        else{
            result.success("SDK token is not initialized...")
        }

    }

    /*private fun performLeadTest(
        testType: EcgTestType, ecgPositionArray: Array<EcgPosition>, start: Int, end: Int
    ) {
        Log.d(TAG, "performLeadTest: EcgTestType --> $testType EcgPositionArray --> $ecgPositionArray")

        //do lead test
        var currentEcgIndex = start
        val lastEcgIndex = end

        span = SpandanSDK.getInstance()
        if (currentEcgIndex < lastEcgIndex) {
            val ecgPositionName = ecgPositionArray[currentEcgIndex]

            ecgTest = span.createTest(testType, object : EcgTestCallback {
                override fun onTestFailed(statusCode: Int) {
                    Log.e(TAG, "onTestFailed: $statusCode")
                    runOnUiThread {
                        resultData.value = "Failed with code $statusCode"
                        shareResultClass.value =
                            ShareLeadData("Error-->Failed with code $statusCode", resultHashMap)
                        Toast.makeText(
                            this@MainActivity,
                            "onTestFailed --->\nString->${shareResultClass.value!!.resultString}\nHashmap-->${shareResultClass.value!!.resultHashMapData}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onTestStarted(ecgPosition: EcgPosition) {
                    Log.d(TAG, "onTestStarted: EcgPosition -> $ecgPosition")
                    runOnUiThread {
                        resultData.value = "Started : $ecgPosition"
                        shareResultClass.value =
                            ShareLeadData("Started : ${ecgPosition.name}", resultHashMap)
                    }
                }

                override fun onElapsedTimeChanged(elapsedTime: Long, remainingTime: Long) {
                    //update only lead 2 progress bar
                    runOnUiThread {
                        resultData.value =
                            "Test Name : $ecgPositionName Remaining ${remainingTime.toInt()} : from ${elapsedTime.toInt()}"
                    }
                }

                override fun onReceivedData(data: String) {
                    //Log.w(TAG, "onReceivedData: $data")
                    runOnUiThread {
//                        resultData.value = "Data : $data"
                        //shareResultClass.value = ShareResultClass("Data : $data", resultHashMap)
                    }
                }

                override fun onPositionRecordingComplete(
                    ecgPosition: EcgPosition, ecgPoints: ArrayList<Double>?
                ) {
                    Log.d(
                        TAG,
                        "onPositionRecordingComplete: EcgPosition --> $ecgPosition : EcgPoints --> ${ecgPoints!!.size}"
                    )

                    //put all the ecgPoints in hashmap to generate report
                    hashMap[ecgPosition] = ecgPoints
                    Toast.makeText(
                        this@MainActivity,
                        "Ecg Position --> $ecgPositionName\n$currentEcgIndex : Test complete out of ${lastEcgIndex - 1}",
                        Toast.LENGTH_SHORT
                    ).show()

                    //add individually ecgPosition points to hashmap
                    val hashMap1 = hashMapOf(
                        ecgPosition.name to ecgPoints
                    )
                    shareResultClass.value = ShareLeadData(ecgPosition.name, hashMap1)

                    //generate report if currentTest is lastTest
                    if (currentEcgIndex == lastEcgIndex - 1) {
                        Toast.makeText(
                            this@MainActivity,
                            "Report Generation work started...",
                            Toast.LENGTH_SHORT
                        ).show()

                        //generate report
                        span.generateReport(32, hashMap, token,
                            object : OnReportGenerationStateListener {
                                override fun onReportGenerationSuccess(ecgReport: EcgReport) {
                                    if (testType == EcgTestType.LEAD_TWO) {
                                        val conclusion = ecgReport.conclusion as LeadTwoConclusion
                                        val characteristics = ecgReport.ecgCharacteristics
                                        Log.d(TAG, "onReportGenerationSuccess:  Conclusion --> $conclusion : Characteristics --> $characteristics")
                                        runOnUiThread {
                                            resultData.value =
                                                "Report Data :::\nDetection : ${conclusion.detection}\n" +
                                                        "EcgType : ${conclusion.ecgType}\n" +
                                                        "BaseLine Wandering : ${conclusion.baselineWandering}\n" +
                                                        "pWave Type : ${conclusion.pWaveType}\n" +
                                                        "QRS Type : ${conclusion.qrsType}\n" +
                                                        "PowerLine Interference : ${conclusion.powerLineInterference}\n"+
                                                        "ECG Data : ${ecgReport.ecgData}"

                                            Toast.makeText(this@MainActivity, "$ecgPositionName : Lead two report successful...${resultData.value}", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    if (testType == EcgTestType.TWELVE_LEAD) {
                                        val conclusion = ecgReport.conclusion as TwelveLeadConclusion
                                        val characteristics = ecgReport.ecgCharacteristics
                                        Log.d(TAG, "onReportGenerationSuccess:  Conclusion --> $conclusion : Characteristics --> $characteristics")
                                        runOnUiThread {
                                            resultData.value =
                                                "Report Data :::\nDetection : ${conclusion.detection}\n" +
                                                        "EcgType : ${conclusion.ecgType}\n" +
                                                        "Anomalies : ${conclusion.anomalies}\n" +
                                                        "Risk : ${conclusion.risk}\n" +
                                                        "Recommendation : ${conclusion.recommendation}\n"+
                                                        "ECG Data : ${ecgReport.ecgData}"
                                            Toast.makeText(this@MainActivity, "$ecgPositionName : Twelve Lead report successful...${resultData.value}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                override fun onReportGenerationFailed(
                                    errorCode: Int,
                                    errorMsg: String
                                ) {
                                    runOnUiThread {
                                        Log.e(TAG, "onReportGenerationFailed: $errorMsg")
                                        Toast.makeText(
                                            this@MainActivity,
                                            errorMsg,
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                }
                            })
                    } else if (currentEcgIndex < lastEcgIndex) {//0 < 1
                        currentEcgIndex++
                        //start another task
                        performLeadTest(testType, ecgPositionArray, currentEcgIndex, lastEcgIndex)
                    }
                }
            }, token)

            ecgTest.start(ecgPositionName)
        }
    }*/

    private fun setUpConnection(result: MethodChannel.Result) {

        /**-------Initialize the token--------**/
        SpandanSDK.initialize(application,
            "enter master key here",
            object : OnInitializationCompleteListener {
                override fun onInitializationSuccess(authenticationToken: String) {
                    result.success("SDK initialized...")
                    runOnUiThread {
                        resultData.value = "SDK Initialized..."
                    }

                    token = authenticationToken
                    Log.d(TAG, "onInitializationSuccess: $authenticationToken")
                }

                override fun onInitializationFailed(message: String) {
                    runOnUiThread {
                        resultData.value = "Failed to initialize device due to $message"
                    }
                    result.success("Failed to initialize device due to $message")
                }
            })

        /**-------bind the sdk--------**/
        SpandanSDK.getInstance().bind(application)

        /**-------Implement the device status callback--------**/
        span = SpandanSDK.getInstance()
        span.setOnDeviceConnectionStateChangedListener(object :
            OnDeviceConnectionStateChangeListener {
            override fun onDeviceConnectionStateChanged(deviceConnectionState: DeviceConnectionState) {
                Log.d(TAG, "onDeviceConnectionStateChanged: $deviceConnectionState")
                runOnUiThread {
                    resultData.value = "Device is $deviceConnectionState"
                }
            }

            override fun onDeviceTypeChange(deviceType: String) {
            }

            override fun onDeviceVerified(deviceInfo: DeviceInfo) {
                Log.d(TAG, "onDeviceConnectionStateChanged: Device Verified $deviceInfo")
                runOnUiThread {
                    resultData.value = "Device is Verified..."
                }
            }

        })

    }
}

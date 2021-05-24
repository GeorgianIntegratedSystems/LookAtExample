package ge.gis.pepperlookatpointat

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.FreeFrame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.conversation.Say
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.actuation.distance
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : RobotActivity(), RobotLifecycleCallbacks, View.OnClickListener {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var qiContext: QiContext

    private lateinit var basicFrame1: FreeFrame
    private lateinit var basicFrame2: FreeFrame
    var extraLookAtFuture: Future<Void>? = null
    val appscope = SingleThread.newCoroutineScope()
    var sayFuture: Future<Say>? = null
    var sayActionFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)
        val saveFrame1: Button = findViewById(R.id.Frame1)
        val saveFrame2: Button = findViewById(R.id.Frame2)
        val runFrame2: Button = findViewById(R.id.runFrame2)
        val runFrame1: Button = findViewById(R.id.runFrame1)
        runFrame2.setOnClickListener(this)
        runFrame1.setOnClickListener(this)
        saveFrame1.setOnClickListener {
            appscope.launch {
                basicFrame1 = qiContext.mapping.makeFreeFrame()
                val robotFrame = qiContext.actuation.robotFrame()
                val transform = TransformBuilder.create().from2DTranslation(0.0, 0.0)
                basicFrame1.update(robotFrame, transform, 0L)
                withContext(Dispatchers.Main.immediate) {
                    saveFrame1.isEnabled = false
                    Toast.makeText(this@MainActivity, "Frame 1 Saved", Toast.LENGTH_LONG).show()
                }
            }
        }

        saveFrame2.setOnClickListener {
            appscope.launch {
                basicFrame2 = qiContext.mapping.makeFreeFrame()
                val robotFrame = qiContext.actuation.robotFrame()
                val transform = TransformBuilder.create().from2DTranslation(0.0, 0.0)
                basicFrame2.update(robotFrame, transform, 0L)
                withContext(Dispatchers.Main.immediate) {
                    saveFrame2.isEnabled = false
                    Toast.makeText(this@MainActivity, "Frame 2 Saved", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onClick(p0: View?) {
        val getFrameName = p0?.tag as String
        Toast.makeText(this@MainActivity, getFrameName, Toast.LENGTH_LONG).show()
        CoroutineScope(Dispatchers.IO).launch {
            runExtraLookat(getFrameName)
        }
    }

    fun ExtraLookAt(freeFrame: FreeFrame) {
        val distance = computeDistance(freeFrame)
        val extraLookAt: ExtraLookAt = ExtraLookAtBuilder.with(qiContext)
                .withFrame(freeFrame.frame())
                .withTerminationPolicy(ExtraLookAt.TerminationPolicy.RUN_FOREVER)
                .build().apply {
                    policy = LookAtMovementPolicy.HEAD_AND_BASE
                }.apply {
                    addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
                        override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                            Log.i("$TAG LookAt", "ExtraLookAt status changes to: $status")
                        }
                    })
                }

        Log.i("$TAG LookAt", "Starting")
        extraLookAtFuture = extraLookAt.async().run()

        if (distance < 5.0) {
            sayFuture = SayBuilder.with(qiContext).withText("THe Object is Near Me. $distance Meter").buildAsync()
            sayActionFuture = sayFuture!!.andThenCompose { say -> say.async().run() }
        } else {
            sayFuture = SayBuilder.with(qiContext).withText("THe Object is $distance Meter from Me").buildAsync()
            sayActionFuture = sayFuture!!.andThenCompose { say -> say.async().run() }
        }
        sayActionFuture!!.andThenConsume {
            extraLookAtFuture!!.requestCancellation()
        }

    }

    fun computeDistance(freeFrame: FreeFrame): Double {
        val robotFrame = qiContext.actuation.robotFrame()

        val distance: Double = robotFrame.distance(freeFrame.frame())

        Log.i("Distance", distance.toString())

        return distance
    }

    fun runExtraLookat(getFrameName: String) {
        when (getFrameName) {
            "RunFrame1" -> ExtraLookAt(basicFrame1)
            "RunFrame2" -> ExtraLookAt(basicFrame2)
            else -> return
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext?) {
        Log.i(TAG, "onRobotFocusGained")
        this.qiContext = qiContext!!
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "onRobotFocusLost")
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "onRobotFocusRefused: $reason")

    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}
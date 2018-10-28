package com.example.tylercichonski.swellstation

import android.app.Activity
import android.app.Application
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.firebase.ui.auth.AuthUI
import java.util.*
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.sdk.pos.ChargeRequest
import com.squareup.sdk.pos.CurrencyCode
import com.squareup.sdk.pos.PosClient
import com.squareup.sdk.pos.PosSdk
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import java.text.DecimalFormat
import java.util.UUID.randomUUID
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    ///////Bluetooth companion object
    companion object {
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String
    }

    private val RC_SIGN_IN = 0
    var transaction = Transaction("",0,"","","", Timestamp.now())
    var location = Location("","","","",0.0,0.0,0.0,0.0,0.0,0.0,0.0)
    lateinit var fs: DocumentReference
    val POINT_OF_CONTACT  = "pointofContact"
    val OZ4_PRICE = "oz4Price"
    val OZ8_PRICE = "oz8Price"
    val OZ12_PRICE = "oz12Price"
    val OZ16_PRICE = "oz16Price"
    val OZ20_Price  = "oz20Price"
    val COST_PER_OUNCE = "costPerOunce"
    val AMOUNT_IN_KEG = "amountInKeg"
    private val APPLICATION_ID = "sq0idp-K9O1x0wOZy9gPTgIDYn-pQ"
    private val CHARGE_REQUEST_CODE = 1
    private var posClient: PosClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        //setupbluetooth

        m_address = "98:D3:31:FD:86:07"
        Log.d("BT","prebluetoth launch")
        ConnectToDevice(this).execute()

        //squareAPI Setup
        posClient = PosSdk.createClient(this,APPLICATION_ID)

        //make interface invisibile
        costBackground.visibility = View.INVISIBLE
        amountDispensedBackground.visibility = View.INVISIBLE
        costTextView.visibility = View.INVISIBLE
        amountDispensedTextView.visibility = View.INVISIBLE
        thisTextView.visibility = View.INVISIBLE
        saleTextView.visibility = View.INVISIBLE
        ouncesTextView.visibility = View.INVISIBLE
        pricePerOunceBackground.visibility = View.INVISIBLE
        pricePerOunceTextView.visibility = View.INVISIBLE
        pricePerOunceTitle.visibility = View.INVISIBLE
        pourInstructionsTextView.visibility = View.INVISIBLE

        oz20Button.setOnClickListener(){
            transaction.cost = location.oz20Price.toInt()
            transaction.coffeeDispensed = "20"
            startPour()
            //squareChargeReqquest()
        }
        oz16Button.setOnClickListener(){
            transaction.cost = location.oz16Price.toInt()
            transaction.coffeeDispensed = "16"
            squareChargeReqquest()
        }
        oz12Button.setOnClickListener(){
            transaction.cost = location.oz12Price.toInt()
            transaction.coffeeDispensed = "12"
            squareChargeReqquest()
        }
        oz8Button.setOnClickListener(){
            transaction.cost = location.oz8Price.toInt()
            transaction.coffeeDispensed = "8"
            squareChargeReqquest()
        }
        oz4Button.setOnClickListener(){
            transaction.cost = location.oz4Price.toInt()
            transaction.coffeeDispensed = "4"
            squareChargeReqquest()
        }



        //sign in current user into firebase
        var mFirebaseAuth = FirebaseAuth.getInstance()
        var mFirebaseUser = mFirebaseAuth.currentUser
        val df = DecimalFormat("#.00")

        //build auth sign-in intent
        val providers = Arrays.asList(
                AuthUI.IdpConfig.GoogleBuilder().build())

        // Create and launch sign-in intent if not already signed in
        if(mFirebaseUser == null) {
            startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
        }

        val db = FirebaseFirestore.getInstance()

        transaction.location = mFirebaseUser?.displayName.toString()
        location.businessName = mFirebaseUser?.displayName.toString()

        //pull and store firestore data
        fs = FirebaseFirestore.getInstance().document("Locations/${transaction.location}")
        fs.get().addOnSuccessListener(OnSuccessListener  <DocumentSnapshot>() { documentSnapshot ->
            location.pointofContact = documentSnapshot.getString("pointofContact")
            location.oz4Price = documentSnapshot.getDouble(OZ4_PRICE)!!
            location.oz8Price = documentSnapshot.getDouble(OZ8_PRICE)!!
            location.oz12Price = documentSnapshot.getDouble(OZ12_PRICE)!!
            location.oz16Price = documentSnapshot.getDouble(OZ16_PRICE)!!
            location.oz20Price = documentSnapshot.getDouble(OZ20_Price)!!
            location.costPerOunce = documentSnapshot.getDouble(COST_PER_OUNCE)!!
            location.amountInKeg = documentSnapshot.getDouble(AMOUNT_IN_KEG)!!
            oz4TextView.setText("$"+df.format(location.oz4Price/100))
            oz8TextView.setText("$"+df.format(location.oz8Price/100))
            oz12TextView.setText("$"+df.format(location.oz12Price/100))
            oz16TextView.setText("$"+df.format(location.oz16Price/100))
            oz20TextView.setText("$"+df.format(location.oz20Price/100))
            fs.set(location, SetOptions.merge())



        })


    }
//control metered output
fun startPour(){
    var costPerOunce : Double= location.costPerOunce
    val df = DecimalFormat("#.00")
    var amountToPour = transaction.cost / 100.toDouble()
    var coffee = 0
    var costDouble = 0.0

    sendCommand("3")
    var job: Job = launch  (UI) {
        //delay(1000)
        while (costDouble<amountToPour) {
            amountToPourTextView.text = amountToPour.toString()

            var coffee = "${coffeeMeter()}"
            delay(100)
            var coffee2 = "${coffeeMeter()}"
            if (coffee2 > coffee) {
                var coffeeDouble = coffee2.toDouble()*.004600544
                if (coffeeDouble  > .6) {
                    amountDispensedTextView.text = df.format(coffeeDouble).toString()
                    var costString = df.format(coffeeDouble * costPerOunce)
                    costTextView.text = "$$costString"
                    coffeeDoubleTextView.text = costDouble.toString()
                    costDouble = coffeeDouble * costPerOunce
                }

            }
        }
        sendCommand("4")
        costTextView.text = "$"+df.format(transaction.cost/100).toString()
        amountDispensedTextView.text = df.format(transaction.cost/location.costPerOunce/100).toString()
        location.amountInKeg = location.amountInKeg - transaction.coffeeDispensed.toInt()
        fs.set(location, SetOptions.merge())
        pourInstructionsTextView.text = "Thank You!"
        val cashRegisterTone = MediaPlayer.create(this@MainActivity,R.raw.registertone)
        cashRegisterTone.start()
        delay(3000)
        restartApp()


    }
    return
}
    fun restartApp(){
        this.recreate()
    }
    suspend fun coffeeMeter() : String= withContext(CommonPool) {
        var buf = ByteArray(1024)
        try {
            var coffeeScanner: Scanner = Scanner(m_bluetoothSocket!!.inputStream).useDelimiter("&")
            "${coffeeScanner.next()}"
        } catch (e: IOException) {
            "exepection"
        }
    }
//setup onClick to start Corutiens
    fun View.onClick(action: suspend (View) -> Unit) {
        // launch one actor
        val eventActor = actor<View>(UI, capacity = Channel.CONFLATED) {
            for (event in channel) action(event)
        }
        // install a listener to activate this actor
        setOnClickListener {
            eventActor.offer(it)
        }
    }




    fun squareChargeReqquest(){

        var request:ChargeRequest = ChargeRequest.
                Builder(transaction.cost, CurrencyCode.USD)
                .restrictTendersTo(ChargeRequest.TenderType.CARD)
                .autoReturn(4, TimeUnit.SECONDS)
                .build()
        try{
            val intent: Intent = posClient?.createChargeIntent(request)!!
            startActivityForResult(intent,CHARGE_REQUEST_CODE)

        }catch (e: IOException) {
            Log.d("error","didnot work")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        costTextView.text = transaction.cost.toString()
        amountDispensedTextView.text = transaction.coffeeDispensed
        if (requestCode == CHARGE_REQUEST_CODE) {
            if (data == null) {
                // This can happen if Square Point of Sale was uninstalled or crashed while we're waiting for a
                // result.
                Toast.makeText(this,"No Result from Square Point of Sale", Toast.LENGTH_SHORT).show()
                return
            }
            if (resultCode == Activity.RESULT_OK) {
                val success = posClient?.parseChargeSuccess(data)!!
                onTransactionSuccess(success)
            } else {
                val error = posClient?.parseChargeError(data)!!
                onTransactionError(error)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }


    private fun onTransactionSuccess(successResult: ChargeRequest.Success) {
        costTextView.text = "$0.00"
        amountDispensedTextView.text = "0.00"
        costBackground.visibility = View.VISIBLE
        amountDispensedBackground.visibility = View.VISIBLE
        costTextView.visibility = View.VISIBLE
        amountDispensedTextView.visibility = View.VISIBLE
        thisTextView.visibility = View.VISIBLE
        saleTextView.visibility = View.VISIBLE
        ouncesTextView.visibility = View.VISIBLE
        pricePerOunceBackground.visibility = View.VISIBLE
        pricePerOunceTextView.visibility = View.VISIBLE
        pricePerOunceTitle.visibility = View.VISIBLE
        oz4Button.visibility = View.INVISIBLE
        oz8Button.visibility = View.INVISIBLE
        oz12Button.visibility = View.INVISIBLE
        oz16Button.visibility = View.INVISIBLE
        oz20Button.visibility = View.INVISIBLE
        oz4TextView.visibility = View.INVISIBLE
        oz8TextView.visibility = View.INVISIBLE
        oz12TextView.visibility = View.INVISIBLE
        oz16TextView.visibility = View.INVISIBLE
        oz20TextView.visibility = View.INVISIBLE
        sizeTextView.visibility = View.INVISIBLE
        pourInstructionsTextView.visibility = View.VISIBLE
        transaction.status = "Paid"
        var locations = fs.collection("Transactions")


        locations.add(transaction).addOnSuccessListener{
            transaction.transactionID = it.id
            fs.collection("Transactions").document(it.id).set(transaction).addOnSuccessListener {
                startPour()
            }
        }
       // this.recreate()
//        val message = Html.fromHtml("<b><font color='#00aa00'>Success</font></b><br><br>"
//                + "<b>Client RealTransaction Id</b><br>"
//                + successResult.clientTransactionId
//                + "<br><br><b>Server RealTransaction Id</b><br>"
//                + successResult.serverTransactionId
//                + "<br><br><b>Request Metadata</b><br>"
//                + successResult.requestMetadata)
//        showResult(message)
    }



    fun onTransactionError(errorResult: ChargeRequest.Error) {
       // fs.collection("Transactions").document("${transaction.transactionID}").set(transaction)
        //setup(cancelRequest)
        //startNewTransaction()
    }

    ////////Bluetooth
    private fun sendCommand(input: String) {
        if (m_bluetoothSocket != null) {
            try{
                m_bluetoothSocket!!.outputStream.write(input.toByteArray())
            } catch(e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun disconnect() {
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        finish()
    }



    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context
        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")

        }

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                Log.d("BT","tried")
                if (m_bluetoothSocket == null || !m_isConnected) {
                    Log.d("BT","m is not connected")
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    Log.d("BT","m_adapter susccess")
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    Log.d("BT","RF commSocket Success")
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    Log.d("BT","calcel discovery success")
                    m_bluetoothSocket!!.connect()
                    Log.d("BT","connection success")


                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
                return
            } else {
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }






}

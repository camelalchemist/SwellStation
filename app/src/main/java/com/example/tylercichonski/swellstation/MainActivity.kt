package com.example.tylercichonski.swellstation

import android.app.Activity
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
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
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DecimalFormat


class MainActivity : AppCompatActivity() {


    private val RC_SIGN_IN = 0
    var transaction = Transaction("","","","","", Timestamp.now())
    var location = Location("","","","","",0.0,0.0,0.0,0.0,0.0)
    lateinit var fs: DocumentReference
    val POINT_OF_CONTACT  = "pointofContact"
    val OZ4_PRICE = "oz4Price"
    val OZ8_PRICE = "oz8Price"
    val OZ12_PRICE = "oz12Price"
    val OZ16_PRICE = "oz16Price"
    val OZ20_Price  = "oz20Price"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //sign in current user into firebase
        var mFirebaseAuth = FirebaseAuth.getInstance()
        var mFirebaseUser = mFirebaseAuth.currentUser
        val df = DecimalFormat("#.00")

        //build sign-in intent
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
            location.oz4Price = documentSnapshot.getDouble(OZ4_PRICE)
            location.oz8Price = documentSnapshot.getDouble(OZ8_PRICE)
            location.oz12Price = documentSnapshot.getDouble(OZ12_PRICE)
            location.oz16Price = documentSnapshot.getDouble(OZ16_PRICE)
            location.oz20Price = documentSnapshot.getDouble(OZ20_Price)
            oz4TextView.setText("$"+df.format(location.oz4Price)).toString()
            oz8TextView.setText("$"+df.format(location.oz8Price)).toString()
            oz12TextView.setText("$"+df.format(location.oz12Price)).toString()
            oz16TextView.setText("$"+df.format(location.oz16Price)).toString()
            oz20TextView.setText("$"+df.format(location.oz20Price)).toString()
            fs.set(location, SetOptions.merge())
        })


    }



}

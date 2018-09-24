package com.example.tylercichonski.swellstation

import com.google.firebase.Timestamp

class Transaction constructor(var coffeeDispensed:String,
                              var cost: String,
                              var status: String,
                              var location: String,
                              var transactionID:String,
                              var timeStamp: Timestamp

)
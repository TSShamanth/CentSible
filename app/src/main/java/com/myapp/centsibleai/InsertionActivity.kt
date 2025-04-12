package com.myapp.centsibleai

import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import java.util.*

class InsertionActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etCategory: AutoCompleteTextView
    private lateinit var etAmount: EditText
    private lateinit var etDate: EditText
    private lateinit var btnSaveData: Button
    private lateinit var radioGroup: RadioGroup
    private lateinit var rbExpense: RadioButton
    private lateinit var rbIncome: RadioButton
    private lateinit var etNote: EditText
    private lateinit var toolbarLinear: LinearLayout
    private var type: Int = 1 //expense is the default value
    private var amount: Double = 0.0
    private var date: Long = 0
    private var invertedDate: Long = 0

    private lateinit var rbSms: RadioButton

    private lateinit var cbRecurring: CheckBox

    private lateinit var dbRef: DatabaseReference //initialize database
    private lateinit var auth: FirebaseAuth

    //to prevent user input the data more than one,
    //the problem usually occur when the network is offline (this app haven't support offline Db),
    //where the user hit the save button multiple times
    private var isSubmitted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insertion)

        //---back button---
        val backButton: ImageButton = findViewById(R.id.backBtn)
        backButton.setOnClickListener {
            finish()
        }
        //--------

        initItem()

        // --Initialize Firebase Auth and firebase database--
        val user = Firebase.auth.currentUser
        val uid = user?.uid
        if (uid != null) {
            dbRef = FirebaseDatabase.getInstance().getReference(uid) //initialize database with uid as the parent
        }
        auth = Firebase.auth
        //----

        //---category menu dropdown---
        etCategory = findViewById(R.id.category)
        val listExpense = CategoryOptions.expenseCategory() //getting the arrayList data from CategoryOptions file
        val expenseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listExpense)
        etCategory.setAdapter(expenseAdapter)
        //------

        //--radio button option choosing---
        radioGroup.setOnCheckedChangeListener { _, checkedID ->
            etCategory.text.clear() //clear the category autocompletetextview when the type changes
            if (checkedID == R.id.rbUpload) {
                cbRecurring.visibility = View.GONE
                val intent = Intent(this, PhotoUploadActivity::class.java)
                startActivity(intent)
            }
            if (checkedID == R.id.rbExpense) {
                cbRecurring.visibility = View.VISIBLE
                type = 1 //expense
                setBackgroundColor()
                etCategory.setAdapter(expenseAdapter) //if expense type selected, the set list expense array in category menu
            }
            if (checkedID == R.id.rbIncome){
                cbRecurring.visibility = View.VISIBLE
                type = 2 //income
                setBackgroundColor()

                //if expense type selected, the set list income array in category menu :
                val listIncome = CategoryOptions.incomeCategory()
                val incomeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listIncome)
                etCategory.setAdapter(incomeAdapter)
            }
            if (checkedID == R.id.rbSms) {
                cbRecurring.visibility = View.GONE
                val intent = Intent(this, SmsImportActivity::class.java)
                startActivity(intent)
            }


        }


        //-----

        //---date picker---
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val currentDate = sdf.parse(sdf.format(System.currentTimeMillis())) //take current date
        date = currentDate!!.time //initialized date value to current date as the default value
        etDate.setOnClickListener {
            clickDatePicker()
        }
        //----


        btnSaveData.setOnClickListener {
            if (!isSubmitted){
                saveTransactionData()
            }else{
                Snackbar.make(findViewById(android.R.id.content), "You have saved the transaction data", Snackbar.LENGTH_LONG).show()
            }

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                parseLatestSmsAndFillForm()
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun parseLatestSmsAndFillForm() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_SMS), 102)
            return
        }

        val cursor = contentResolver.query(
            android.net.Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val body = it.getString(it.getColumnIndexOrThrow("body"))
                val sender = it.getString(it.getColumnIndexOrThrow("address"))

                if (isExpenseMessage(body)) {
                    val extractedAmount = extractAmount(body)
                    if (extractedAmount != null) {
                        etAmount.setText(extractedAmount.toString())
                        etTitle.setText("SMS Transaction")
                        etCategory.setText("Banking", false)
                        etNote.setText(body.take(80))
                        return
                    }
                }
            }
        }

        Toast.makeText(this, "No suitable SMS found", Toast.LENGTH_SHORT).show()
    }

    private fun isExpenseMessage(message: String): Boolean {
        return message.contains("debited", ignoreCase = true) ||
                message.contains("purchase", ignoreCase = true) ||
                message.contains("spent", ignoreCase = true)
    }

    private fun extractAmount(message: String): Double? {
        val regex = Regex("(INR|Rs\\.?|₹)\\s?([0-9,]+\\.?[0-9]*)")
        val match = regex.find(message)
        return match?.groups?.get(2)?.value?.replace(",", "")?.toDoubleOrNull()
    }


    private fun setBackgroundColor() {
        if (type == 1){
            rbExpense.setBackgroundResource(R.drawable.radio_selected_expense)
            rbIncome.setBackgroundResource(R.drawable.radio_not_selected)
            toolbarLinear.setBackgroundResource(R.drawable.bg_insert_expense)
            btnSaveData.backgroundTintList = getColorStateList(R.color.orangePrimary)
            window.statusBarColor = ContextCompat.getColor(this, R.color.orangePrimary)
        }else{
            rbIncome.setBackgroundResource(R.drawable.radio_selected_income)
            rbExpense.setBackgroundResource(R.drawable.radio_not_selected)
            toolbarLinear.setBackgroundResource(R.drawable.bg_insert_income)
            btnSaveData.backgroundTintList = getColorStateList(R.color.toscaSecondary)
            window.statusBarColor = ContextCompat.getColor(this, R.color.toscaSecondary)
        }
    }

    private fun initItem() {
        etTitle = findViewById(R.id.title)
        etCategory = findViewById(R.id.category)
        etAmount = findViewById(R.id.amount)
        etDate = findViewById(R.id.date)
        btnSaveData = findViewById(R.id.saveButton)
        radioGroup = findViewById(R.id.typeRadioGroup)
        rbExpense = findViewById(R.id.rbExpense)
        rbIncome = findViewById(R.id.rbIncome)
        etNote = findViewById(R.id.note)
        toolbarLinear = findViewById(R.id.toolbarLinear)
        cbRecurring = findViewById(R.id.recurringCheckBox)
        rbSms = findViewById(R.id.rbSms)
    }

    private fun clickDatePicker() {
        val myCalendar = Calendar.getInstance()
        val year = myCalendar.get(Calendar.YEAR)
        val month = myCalendar.get(Calendar.MONTH)
        val day = myCalendar.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->

                val selectedDate = "$selectedDayOfMonth/${selectedMonth + 1}/$selectedYear"
                etDate.text = null
                etDate.hint = selectedDate

                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                val theDate = sdf.parse(selectedDate)
                date = theDate!!.time //convert date to millisecond

            },
            year,
            month,
            day
        )
        dpd.show()
    }

    private fun saveTransactionData() {
        //getting values from form input user:
        val title = etTitle.text.toString()
        val category = etCategory.text.toString()
        val amountEt = etAmount.text.toString()
        val note = etNote.text.toString()
        val recurring = cbRecurring.isChecked


        if(amountEt.isEmpty()){
            etAmount.error = "Please enter Amount"
        }else if(title.isEmpty()) {
            etTitle.error = "Please enter Title"
        }else if(category.isEmpty()){
            etCategory.error = "Please enter Category"
        }else{
            amount = etAmount.text.toString().toDouble() //convert to double type

            val transactionID = dbRef.push().key!!
            invertedDate = date * -1 //convert millis value to negative, so it can be sort as descending order
            val transaction = TransactionModel(transactionID, type, title, category, amount, date, if (recurring) "$note (Recurring)" else note, invertedDate, recurring) //object of data class

            dbRef.child(transactionID).setValue(transaction)
                .addOnCompleteListener {
                    Toast.makeText(this, "Data Inserted Successfully", Toast.LENGTH_LONG).show()
                    finish()
                }.addOnFailureListener { err ->
                    Toast.makeText(this, "Error ${err.message}", Toast.LENGTH_LONG).show()
                }

            isSubmitted = true
        }


    }



}


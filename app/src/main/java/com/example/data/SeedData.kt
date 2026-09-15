package com.example.data

object SeedData {

  val batch10thSciA = Batch(
    id = "BATCH_10_SCI_A",
    name = "10th - Science (A)",
    schedule = "08:00 AM – 09:30 AM",
    studentCount = 4,
    teacherName = "Prof. Shrirang Deshmukh"
  )

  val defaultBatches = listOf(
    Batch("BATCH_8TH", "8th Standard", "07:30 AM – 09:00 AM", 0, "Prof. Akash Joshi"),
    batch10thSciA,
    Batch("BATCH_11_SCI_B", "11th - Science (B)", "09:45 AM – 11:15 AM", 2, "Dr. Faisal Patel"),
    Batch("BATCH_JEE_APEX_M", "JEE Apex Morning", "11:30 AM – 01:00 PM", 2, "Prof. Akash Joshi"),
    Batch("BATCH_FOUNDATION_T", "Foundation Target", "04:00 PM – 05:30 PM", 2, "Prof. Shrirang Deshmukh"),
    Batch("BATCH_10_SCI_B", "10th - Science (B)", "05:45 PM – 07:15 PM", 2, "Prof. Akash Joshi"),
    Batch("BATCH_12_SCI", "12th Science", "08:00 AM – 01:30 PM", 2, "Prof. Shrirang Deshmukh"),
    Batch("BATCH_11_SCI", "11th Science", "02:00 PM – 06:30 PM", 0, "Dr. Faisal Patel"),
    Batch("BATCH_NEET", "NEET Zenith", "08:00 AM – 02:00 PM", 1, "Dr. Faisal Patel")
  )

  val defaultSubjects = listOf(
    Subject("SUB_PHY_12", "12th Physics", 75, listOf("Electrostatics", "Current Electricity", "Magnetism", "Optics"), listOf("Wave Optics", "Dual Nature", "Atoms & Nuclei", "Semiconductors")),
    Subject("SUB_CHEM_12", "12th Chemistry", 80, listOf("Solid State", "Solutions", "Electrochemistry", "Chemical Kinetics"), listOf("Surface Chemistry", "p-Block Elements", "Coordination Compounds")),
    Subject("SUB_MATH_12", "12th Mathematics", 70, listOf("Matrices", "Determinants", "Continuity & Differentiability", "Application of Derivatives"), listOf("Integrals", "Differential Equations", "Vectors", "3D Geometry")),
    Subject("SUB_BIO_12", "12th Biology", 85, listOf("Reproduction in Organisms", "Genetics & Evolution", "Human Welfare"), listOf("Biotechnology", "Ecology & Environment"))
  )

  val defaultTeachers = listOf(
    Teacher(
      id = "FAC_01",
      name = "Prof. Shrirang Deshmukh",
      subject = "Physics",
      contact = "+91 98221 11001",
      qualification = "M.Sc. Physics (IIT Bombay)",
      experience = "12 Years",
      assignedBatches = listOf("10th - Science (A)", "12th Science", "Foundation Target"),
      classesTaken = 142,
      attendancePercent = 98,
      feedbackRating = 4.9f,
      salary = 85000,
      incentives = 10000,
      deductions = 0,
      status = "Active",
      email = "deshmukh.physics@mytuition.com"
    ),
    Teacher(
      id = "FAC_02",
      name = "Dr. Faisal Patel",
      subject = "Chemistry",
      contact = "+91 98221 11002",
      qualification = "Ph.D. Organic Chemistry",
      experience = "10 Years",
      assignedBatches = listOf("11th - Science (B)", "11th Science", "NEET Zenith"),
      classesTaken = 128,
      attendancePercent = 96,
      feedbackRating = 4.8f,
      salary = 80000,
      incentives = 8000,
      deductions = 0,
      status = "Active",
      email = "patel.chem@mytuition.com"
    ),
    Teacher(
      id = "FAC_03",
      name = "Prof. Akash Joshi",
      subject = "Mathematics",
      contact = "+91 98221 11003",
      qualification = "M.Tech Mathematics & Computing",
      experience = "9 Years",
      assignedBatches = listOf("8th Standard", "10th - Science (B)", "JEE Apex Morning"),
      classesTaken = 135,
      attendancePercent = 99,
      feedbackRating = 5.0f,
      salary = 82000,
      incentives = 9000,
      deductions = 0,
      status = "Active",
      email = "joshi.maths@mytuition.com"
    )
  )

  val rawStudentNames = listOf(
    "Shrirang Deshmukh",
    "Atharv Deshmukh",
    "Krushna Atole",
    "Faisal Patel",
    "Krushna Chavan",
    "Mahadev Chavan",
    "Sumit Sapkal",
    "Pavan Kangne",
    "Vaibhav Sunwade",
    "Sumedh Palaskar",
    "Kartik Samgle",
    "Prathmesh Takle",
    "Akash Joshi",
    "Samadhan Ugle",
    "Samgharsh Gadhe",
    "Shreyash Jadhav",
    "Areeb Khan",
    "Avdhut Raut",
    "Sarvesh Dixit",
    "Vinayak Kale",
    "Rohan Dhole",
    "Vedant Ugle",
    "Aditaj Katkde",
    "Rajveer Kedare",
    "ShriJay Agle",
    "Om Hirekar",
    "Daksh More",
    "Rudra Bhandrge",
    "Kaushma Shinde",
    "Pruthvising Jadhav",
    "Rameshwar Gitam",
    "Om Thange",
    "Amol Shelke",
    "Shivam Tekale",
    "Shubham Vishwakarma",
    "Praful Mundhe",
    "Omkar Giri",
    "Balraje Kadarg",
    "Kshitij Deshmukh",
    "Abhay Lad",
    "Om Divyaveer",
    "Soham Chavan",
    "Karan Gadge",
    "Kishor Mule",
    "Yogesh Paul",
    "Harshwardhan Ugale",
    "Chinmay Deshmukh",
    "Shivam Dhoke",
    "Mukund Yewle",
    "Atharv Kale",
    "Sagar Rathod",
    "Yash Chavan",
    "Atharv Joshi"
  )

  fun get15DemoStudents(): List<Student> {
    return listOf(
      Student(
        id = "STU1001",
        name = "Rahul Sharma",
        mobile = "+91 98230 1001",
        parentName = "Mr. Rajesh Sharma",
        parentContact = "+91 98220 1001",
        email = "rahul.sharma@mytuition.com",
        dob = "12/04/2009",
        gender = "Male",
        address = "Cidco N-2, Chhatrapati Sambhajinagar",
        school = "FSI High School",
        className = "Class 10",
        batch = "10th - Science (A)",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = 92,
        overallAvg = 88,
        rank = 1,
        strongestSubject = "Science",
        weakestSubject = "Mathematics",
        recentScores = listOf("Science" to 92, "Mathematics" to 84, "English" to 88)
      ),
      Student(
        id = "STU1002",
        name = "Ananya Singh",
        mobile = "+91 98230 1002",
        parentName = "Mr. Vikram Singh",
        parentContact = "+91 98220 1002",
        email = "ananya.singh@mytuition.com",
        dob = "24/08/2009",
        gender = "Female",
        address = "Garkheda Parisar, Chhatrapati Sambhajinagar",
        school = "FSI High School",
        className = "Class 10",
        batch = "10th - Science (A)",
        stream = "Science",
        admissionDate = "02/06/2026",
        status = "Active",
        attendancePercent = 96,
        overallAvg = 91,
        rank = 2,
        strongestSubject = "Mathematics",
        weakestSubject = "Social Studies",
        recentScores = listOf("Science" to 89, "Mathematics" to 95, "English" to 90)
      ),
      Student(
        id = "STU1003",
        name = "Karan Patel",
        mobile = "+91 98230 1003",
        parentName = "Mr. Suresh Patel",
        parentContact = "+91 98220 1003",
        email = "karan.patel@mytuition.com",
        dob = "05/11/2009",
        gender = "Male",
        address = "Nageshwarwadi, Chhatrapati Sambhajinagar",
        school = "St. Lawrence School",
        className = "Class 10",
        batch = "10th - Science (A)",
        stream = "Science",
        admissionDate = "03/06/2026",
        status = "Active",
        attendancePercent = 78,
        overallAvg = 76,
        rank = 12,
        strongestSubject = "Physics",
        weakestSubject = "Chemistry",
        recentScores = listOf("Science" to 78, "Mathematics" to 74, "English" to 76)
      ),
      Student(
        id = "STU1004",
        name = "Neha Gupta",
        mobile = "+91 98230 1004",
        parentName = "Mr. Anil Gupta",
        parentContact = "+91 98220 1004",
        email = "neha.gupta@mytuition.com",
        dob = "18/02/2009",
        gender = "Female",
        address = "Usmanpura, Chhatrapati Sambhajinagar",
        school = "Podar International",
        className = "Class 10",
        batch = "10th - Science (A)",
        stream = "Science",
        admissionDate = "04/06/2026",
        status = "Active",
        attendancePercent = 89,
        overallAvg = 85,
        rank = 5,
        strongestSubject = "Chemistry",
        weakestSubject = "Physics",
        recentScores = listOf("Science" to 88, "Mathematics" to 82, "English" to 86)
      ),
      Student(
        id = "STU1201",
        name = "Shrirang Deshmukh",
        mobile = "+91 98230 3001",
        parentName = "Mr. Ramesh Deshmukh",
        parentContact = "+91 98220 3001",
        email = "shrirang.deshmukh@mytuition.com",
        dob = "15/06/2007",
        gender = "Male",
        address = "Chhatrapati Sambhajinagar, Maharashtra",
        school = "FSI Junior College of Science",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = 94,
        overallAvg = 93,
        rank = 1,
        strongestSubject = "Physics",
        weakestSubject = "Chemistry",
        recentScores = listOf("Physics" to 95, "Chemistry" to 90, "Mathematics" to 94)
      ),
      Student(
        id = "STU1202",
        name = "Atharv Deshmukh",
        mobile = "+91 98230 3002",
        parentName = "Mr. Vilas Deshmukh",
        parentContact = "+91 98220 3002",
        email = "atharv.deshmukh@mytuition.com",
        dob = "20/09/2007",
        gender = "Male",
        address = "Chhatrapati Sambhajinagar",
        school = "FSI Junior College of Science",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = 90,
        overallAvg = 87,
        rank = 3,
        strongestSubject = "Mathematics",
        weakestSubject = "Physics",
        recentScores = listOf("Physics" to 84, "Chemistry" to 88, "Mathematics" to 90)
      ),
      Student(
        id = "STU1101",
        name = "Krushna Atole",
        mobile = "+91 98230 3003",
        parentName = "Mr. Prakash Atole",
        parentContact = "+91 98220 3003",
        email = "krushna.atole@mytuition.com",
        dob = "10/01/2008",
        gender = "Male",
        address = "Beed Bypass, Chhatrapati Sambhajinagar",
        school = "SB College of Science",
        className = "Class 11",
        batch = "11th - Science (B)",
        stream = "Science",
        admissionDate = "05/06/2026",
        status = "Active",
        attendancePercent = 88,
        overallAvg = 84,
        rank = 4,
        strongestSubject = "Chemistry",
        weakestSubject = "Biology",
        recentScores = listOf("Physics" to 82, "Chemistry" to 88, "Biology" to 82)
      ),
      Student(
        id = "STU1102",
        name = "Faisal Patel",
        mobile = "+91 98230 3004",
        parentName = "Mr. Salim Patel",
        parentContact = "+91 98220 3004",
        email = "faisal.patel@mytuition.com",
        dob = "03/05/2008",
        gender = "Male",
        address = "Shahnoorwadi, Chhatrapati Sambhajinagar",
        school = "Deogiri College",
        className = "Class 11",
        batch = "11th - Science (B)",
        stream = "Science",
        admissionDate = "06/06/2026",
        status = "Active",
        attendancePercent = 91,
        overallAvg = 89,
        rank = 2,
        strongestSubject = "Physics",
        weakestSubject = "Mathematics",
        recentScores = listOf("Physics" to 92, "Chemistry" to 87, "Mathematics" to 88)
      ),
      Student(
        id = "STU1301",
        name = "Akash Joshi",
        mobile = "+91 98230 3013",
        parentName = "Mr. Mohan Joshi",
        parentContact = "+91 98220 3013",
        email = "akash.joshi@mytuition.com",
        dob = "17/12/2007",
        gender = "Male",
        address = "Kranti Chowk, Chhatrapati Sambhajinagar",
        school = "FSI Junior College",
        className = "Class 12",
        batch = "JEE Apex Morning",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = 98,
        overallAvg = 96,
        rank = 1,
        strongestSubject = "Mathematics",
        weakestSubject = "Chemistry",
        recentScores = listOf("Physics" to 96, "Chemistry" to 94, "Mathematics" to 98)
      ),
      Student(
        id = "STU1302",
        name = "Areeb Khan",
        mobile = "+91 98230 3017",
        parentName = "Mr. Tariq Khan",
        parentContact = "+91 98220 3017",
        email = "areeb.khan@mytuition.com",
        dob = "22/07/2007",
        gender = "Male",
        address = "Roshan Gate, Chhatrapati Sambhajinagar",
        school = "Maulana Azad College",
        className = "Class 12",
        batch = "JEE Apex Morning",
        stream = "Science",
        admissionDate = "02/06/2026",
        status = "Active",
        attendancePercent = 93,
        overallAvg = 90,
        rank = 3,
        strongestSubject = "Physics",
        weakestSubject = "Mathematics",
        recentScores = listOf("Physics" to 94, "Chemistry" to 88, "Mathematics" to 88)
      ),
      Student(
        id = "STU1401",
        name = "Pavan Kangne",
        mobile = "+91 98230 3008",
        parentName = "Mr. Dnyaneshwar Kangne",
        parentContact = "+91 98220 3008",
        email = "pavan.kangne@mytuition.com",
        dob = "14/03/2010",
        gender = "Male",
        address = "Mukundwadi, Chhatrapati Sambhajinagar",
        school = "FSI High School",
        className = "Class 9",
        batch = "Foundation Target",
        stream = "Science",
        admissionDate = "10/06/2026",
        status = "Active",
        attendancePercent = 87,
        overallAvg = 82,
        rank = 6,
        strongestSubject = "Science",
        weakestSubject = "English",
        recentScores = listOf("Science" to 85, "Mathematics" to 80, "English" to 81)
      ),
      Student(
        id = "STU1402",
        name = "Vaibhav Sunwade",
        mobile = "+91 98230 3009",
        parentName = "Mr. Shivaji Sunwade",
        parentContact = "+91 98220 3009",
        email = "vaibhav.sunwade@mytuition.com",
        dob = "29/10/2010",
        gender = "Male",
        address = "Waluj MIDC, Chhatrapati Sambhajinagar",
        school = "FSI High School",
        className = "Class 9",
        batch = "Foundation Target",
        stream = "Science",
        admissionDate = "11/06/2026",
        status = "Active",
        attendancePercent = 85,
        overallAvg = 79,
        rank = 9,
        strongestSubject = "Mathematics",
        weakestSubject = "Science",
        recentScores = listOf("Science" to 76, "Mathematics" to 83, "English" to 78)
      ),
      Student(
        id = "STU1005",
        name = "Shreyash Jadhav",
        mobile = "+91 98230 3016",
        parentName = "Mr. Balasaheb Jadhav",
        parentContact = "+91 98220 3016",
        email = "shreyash.jadhav@mytuition.com",
        dob = "08/08/2009",
        gender = "Male",
        address = "Seven Hills, Chhatrapati Sambhajinagar",
        school = "St. Francis High School",
        className = "Class 10",
        batch = "10th - Science (B)",
        stream = "Science",
        admissionDate = "05/06/2026",
        status = "Active",
        attendancePercent = 90,
        overallAvg = 86,
        rank = 2,
        strongestSubject = "Science",
        weakestSubject = "Social Studies",
        recentScores = listOf("Science" to 88, "Mathematics" to 84, "English" to 86)
      ),
      Student(
        id = "STU1006",
        name = "Om Hirekar",
        mobile = "+91 98230 3026",
        parentName = "Mr. Pandurang Hirekar",
        parentContact = "+91 98220 3026",
        email = "om.hirekar@mytuition.com",
        dob = "19/01/2009",
        gender = "Male",
        address = "Padampura, Chhatrapati Sambhajinagar",
        school = "FSI High School",
        className = "Class 10",
        batch = "10th - Science (B)",
        stream = "Science",
        admissionDate = "06/06/2026",
        status = "Active",
        attendancePercent = 84,
        overallAvg = 81,
        rank = 5,
        strongestSubject = "Mathematics",
        weakestSubject = "Science",
        recentScores = listOf("Science" to 79, "Mathematics" to 84, "English" to 80)
      ),
      Student(
        id = "STU1501",
        name = "Rudra Bhandrge",
        mobile = "+91 98230 3028",
        parentName = "Mr. Sanjay Bhandrge",
        parentContact = "+91 98220 3028",
        email = "rudra.bhandrge@mytuition.com",
        dob = "11/09/2007",
        gender = "Male",
        address = "Osmanpura, Chhatrapati Sambhajinagar",
        school = "Deogiri Junior College",
        className = "Class 12",
        batch = "NEET Zenith",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = 95,
        overallAvg = 92,
        rank = 1,
        strongestSubject = "Biology",
        weakestSubject = "Physics",
        recentScores = listOf("Physics" to 88, "Chemistry" to 92, "Biology" to 96)
      )
    )
  }

  fun get12thScienceStudents(): List<Student> {
    return rawStudentNames.mapIndexed { index, name ->
      val srNo = index + 1
      val stuId = "STU12S" + String.format("%02d", srNo)
      val nameParts = name.trim().split(" ")
      val firstName = nameParts.firstOrNull() ?: "Student"
      val surname = nameParts.lastOrNull() ?: "Parent"
      val emailFormatted = "${firstName.lowercase()}.${surname.lowercase()}@mytuition.com"

      val parentName = "Mr. ${surname} (Parent)"
      val parentMobile = "+91 98220 " + String.format("%04d", 3000 + srNo)
      val studentMobile = "+91 98230 " + String.format("%04d", 4000 + srNo)

      val attPct = 82 + ((srNo * 7) % 18)
      val overallAvg = 72 + ((srNo * 11) % 25)

      val strongSub = when (srNo % 3) {
        0 -> "Physics"
        1 -> "Chemistry"
        else -> "Mathematics"
      }
      val weakSub = when (srNo % 3) {
        0 -> "Mathematics"
        1 -> "Physics"
        else -> "Chemistry"
      }

      Student(
        id = stuId,
        name = name,
        photo = "",
        mobile = studentMobile,
        parentName = parentName,
        parentContact = parentMobile,
        email = emailFormatted,
        dob = "15/06/2007",
        gender = "Male",
        address = "Chhatrapati Sambhajinagar, Maharashtra",
        school = "FSI Junior College of Science",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        attendancePercent = attPct,
        overallAvg = overallAvg,
        rank = srNo,
        strongestSubject = strongSub,
        weakestSubject = weakSub,
        recentScores = listOf(
          "Physics" to (overallAvg - 4).coerceAtLeast(60),
          "Chemistry" to overallAvg,
          "Mathematics" to (overallAvg + 3).coerceAtMost(100)
        )
      )
    }
  }

  fun getDefaultFeeRecords(): List<FeeRecord> {
    val demoStudents = get15DemoStudents()
    return demoStudents.mapIndexed { idx, stu ->
      val monthlyFee = 6400
      val (paid, status, dueDate) = when {
        idx < 10 -> Triple(6400, "Paid", "10/08/2026")
        idx < 13 -> Triple(3200, "Pending", "15/08/2026")
        else -> Triple(0, "Overdue", "05/08/2026")
      }
      val pending = monthlyFee - paid
      FeeRecord(
        id = "F_2026_AUG_${idx + 1}",
        studentName = stu.name,
        feeAmount = monthlyFee,
        dueDate = dueDate,
        paidAmount = paid,
        pendingAmount = pending,
        paymentStatus = status,
        month = "August 2026"
      )
    }
  }

  fun getDefaultFeePayments(): List<StudentFeePayment> {
    val monthsData = listOf(
      "April 2026" to listOf(
        Triple("10/04/2026", 28000.0, "Paid"),
        Triple("18/04/2026", 32000.0, "Paid"),
        Triple("26/04/2026", 24000.0, "Paid"),
        Triple("30/04/2026", 12000.0, "Pending")
      ),
      "May 2026" to listOf(
        Triple("06/05/2026", 32000.0, "Paid"),
        Triple("16/05/2026", 35000.0, "Paid"),
        Triple("24/05/2026", 25000.0, "Paid"),
        Triple("31/05/2026", 4000.0, "Pending")
      ),
      "June 2026" to listOf(
        Triple("05/06/2026", 45000.0, "Paid"),
        Triple("15/06/2026", 42000.0, "Paid"),
        Triple("28/06/2026", 25000.0, "Paid"),
        Triple("30/06/2026", 8000.0, "Pending")
      ),
      "July 2026" to listOf(
        Triple("08/07/2026", 30000.0, "Paid"),
        Triple("18/07/2026", 32000.0, "Paid"),
        Triple("28/07/2026", 26000.0, "Paid"),
        Triple("31/07/2026", 8000.0, "Pending")
      ),
      "August 2026" to listOf(
        Triple("04/08/2026", 34000.0, "Paid"),
        Triple("12/08/2026", 26000.0, "Paid"),
        Triple("20/08/2026", 13600.0, "Paid"),
        Triple("22/08/2026", 9600.0, "Pending"),
        Triple("25/08/2026", 12800.0, "Overdue")
      )
    )
    val modes = listOf("UPI", "Cash", "Net Banking", "UPI", "Card")
    val students = get15DemoStudents()
    val payments = mutableListOf<StudentFeePayment>()
    var pIdx = 0

    monthsData.forEachIndexed { mIdx, (mth, transactions) ->
      transactions.forEachIndexed { dIdx, (dateStr, amt, status) ->
        val stu = students[pIdx % students.size]
        val mode = modes[pIdx % modes.size]
        
        payments.add(
          StudentFeePayment(
            id = "PAY_2026_${1001 + pIdx}",
            studentId = stu.id,
            studentName = stu.name,
            amount = amt,
            paymentDate = dateStr,
            paymentStatus = status,
            feeType = if (pIdx % 3 == 0) "Monthly Tuition" else if (pIdx % 3 == 1) "Lab & Study Material" else "Installment 2",
            paymentMode = mode,
            receiptNo = "REC-2026-${1001 + pIdx}",
            month = mth,
            remarks = "Session 2026-27 tuition installment",
            recordedBy = "Admin",
            timestamp = System.currentTimeMillis() - ((4 - mIdx) * 30L * 86400000L) - (dIdx * 86400000L * 2)
          )
        )
        pIdx++
      }
    }
    return payments
  }

  fun getDefaultTestRecords(): List<TestRecord> {
    val students = get12thScienceStudents()
    val marksMap = students.associate { it.id to (120 + (it.rank * 3) % 80) }
    return listOf(
      TestRecord(
        id = "TST_12S_01",
        testName = "12th Science - Major Test 1 (Electrostatics & Organic Chem)",
        subject = "12th Physics & Chemistry",
        date = "20/07/2026",
        batch = "12th Science",
        totalMarks = 200,
        studentMarks = marksMap,
        remarks = "Overall excellent batch performance in Electrostatics",
        aiAnalysisStrong = listOf("Electrostatics Field Calculations", "Nomenclature & Isomerism"),
        aiAnalysisWeak = listOf("Capacitance Edge Cases", "Reaction Mechanisms"),
        aiSuggestion = "Schedule 2 dedicated doubt clearance sessions on Capacitance for 12th Science batch."
      )
    )
  }

  fun getDefaultTimetableEntries(): List<DynamicTimetableEntry> {
    val now = System.currentTimeMillis()
    return listOf(
      // Active entries (within 23 hours)
      DynamicTimetableEntry(
        id = "DTT_SEED_01",
        subject = "Physics • Wave Optics & Interference",
        batch = "11th Science",
        startTime = "02:00 PM",
        endTime = "03:30 PM",
        createdAt = now - (2 * 60 * 60 * 1000L), // 2 hours ago -> 21 hours left
        teacherName = "Prof. Shrirang Deshmukh"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_02",
        subject = "Chemistry • Organic Reaction Mechanisms",
        batch = "11th Science",
        startTime = "03:45 PM",
        endTime = "05:15 PM",
        createdAt = now - (2 * 60 * 60 * 1000L),
        teacherName = "Dr. Faisal Patel"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_03",
        subject = "Mathematics • Advanced Calculus & Limits",
        batch = "JEE Apex Morning",
        startTime = "11:30 AM",
        endTime = "01:00 PM",
        createdAt = now - (4 * 60 * 60 * 1000L), // 4 hours ago -> 19 hours left
        teacherName = "Prof. Akash Joshi"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_04",
        subject = "Physics • Modern Physics & Quantum Mechanics",
        batch = "12th Science",
        startTime = "08:00 AM",
        endTime = "09:30 AM",
        createdAt = now - (6 * 60 * 60 * 1000L), // 6 hours ago -> 17 hours left
        teacherName = "Prof. Shrirang Deshmukh"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_05",
        subject = "Mathematics • Linear Equations & Graphing",
        batch = "8th Standard",
        startTime = "07:30 AM",
        endTime = "09:00 AM",
        createdAt = now - (3 * 60 * 60 * 1000L), // 3 hours ago -> 20 hours left
        teacherName = "Prof. Akash Joshi"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_06",
        subject = "Biology • Genetics & Molecular Inheritance",
        batch = "NEET Zenith",
        startTime = "08:00 AM",
        endTime = "09:30 AM",
        createdAt = now - (5 * 60 * 60 * 1000L), // 5 hours ago -> 18 hours left
        teacherName = "Dr. Faisal Patel"
      ),
      // Archived entries (strictly > 23 hours ago, permanently stored in database)
      DynamicTimetableEntry(
        id = "DTT_SEED_ARCHIVE_01",
        subject = "Physics • Ray Optics Numerical Drill",
        batch = "12th Science",
        startTime = "08:00 AM",
        endTime = "09:30 AM",
        createdAt = now - (26 * 60 * 60 * 1000L), // 26 hours ago -> Archived
        teacherName = "Prof. Shrirang Deshmukh"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_ARCHIVE_02",
        subject = "Mathematics • Vectors & 3D Geometry PYQs",
        batch = "JEE Apex Morning",
        startTime = "11:30 AM",
        endTime = "01:00 PM",
        createdAt = now - (28 * 60 * 60 * 1000L), // 28 hours ago -> Archived
        teacherName = "Prof. Akash Joshi"
      ),
      DynamicTimetableEntry(
        id = "DTT_SEED_ARCHIVE_03",
        subject = "Science • Force & Pressure Practical Concepts",
        batch = "8th Standard",
        startTime = "07:30 AM",
        endTime = "09:00 AM",
        createdAt = now - (48 * 60 * 60 * 1000L), // 48 hours ago -> Archived
        teacherName = "Prof. Shrirang Deshmukh"
      )
    )
  }
}

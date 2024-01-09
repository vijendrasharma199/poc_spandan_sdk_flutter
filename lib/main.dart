import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.green,
      ),
      home: const MyHomePage(title: 'Flutter SDK POC'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform = MethodChannel('com.example.poc_spandan_sdk/sericom');
  static const _stream = EventChannel('com.example.poc_spandan_sdk/sericom_event');

  TextEditingController commandController = TextEditingController();

  //variables
  String _deviceInfoStatus = '';
  int counter = 0;
  final StringBuffer _receivedData = StringBuffer('');

  @override
  void initState() {
    //set up connection
    setupDeviceConnection();
    super.initState();
  }

  void initializeStream() {
    _stream.receiveBroadcastStream().listen((dynamic data) {
      setState(() {
        _receivedData.writeln(data + "\n---------------------");
      });
    });
  }

  //Step 1. setup Device Connection
  void setupDeviceConnection() async {
    String response = "";
    try {
      final String deviceConnectionStatus =
          await platform.invokeMethod('setUpConnection');
      response = "Device Status --> $deviceConnectionStatus";

      //initialize stream
      initializeStream();
    } on PlatformException catch (e) {
      response = "Failed to get device data: '${e.message}'.";
    }

    //update view
    setState(() {
      _deviceInfoStatus = response;
    });
  }

  //Step 2. create Test
  void createTest() async {
    String response = "";
    try {
      response = await platform
          .invokeMethod("createTest", {"testType": commandController.text});
    } on PlatformException catch (e) {
      response = "Failed to get device data: '${e.message}'.";
    }
  }

  //Step 3. start ecg test ecgPosition wise
  void startEcgTest(ecgPositionName) {
    print(ecgPositionName);
    sendCommand(ecgPositionName);
  }

  //send command ecgPosition wise
  void sendCommand(command) async {
    String response = "";
    try {
      final bool startCommand =
          await platform.invokeMethod("sendCommand", {"leadName": command});
      response = startCommand.toString();
    } on PlatformException catch (e) {
      response = "Failed to get device data: '${e.message}'.";
    }
  }

  //Step 4. generate report
  void generateReport() async {
    String response = "";
    try {
      response = await platform
          .invokeMethod("generateReport", {"testType": commandController.text});
    } on PlatformException catch (e) {
      response = "Failed to get device data: '${e.message}'.";
    }
  }

  // void updateData(String data, bool isDataWithCounter) {
  //   setState(() {
  //     if (isDataWithCounter) {
  //       ++counter;
  //       String countValue = counter.toString();
  //       _receivedData.writeln("$data : Counter--> $countValue");
  //     } else {
  //       _receivedData.writeln(data);
  //     }
  //   });
  // }

  @override
  Widget build(BuildContext context) {
    var ecgList = ['V1', 'V2', 'V3', 'V4', 'V5', 'V6', 'Lead_1', 'Lead_2'];

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: MediaQuery.of(context).size.width,
              margin: const EdgeInsets.all(8),
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(border: Border.all(color: Colors.black), color: Colors.white),
              child: Expanded(
                flex: 1,
                child: SingleChildScrollView(
                  scrollDirection: Axis.vertical, //.horizontal
                  reverse: true,
                  child: Text(
                    _receivedData.toString(),
                    textAlign: TextAlign.start,
                    style: const TextStyle(
                      fontSize: 12.0,
                      color: Colors.black87,
                    ),
                  ),
                ),
              ),
            ),
          ),
          Container(
            margin: const EdgeInsets.only(top: 12, left: 8, right: 8),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ElevatedButton(
                  onPressed: () {
                    //setup connection
                    setupDeviceConnection();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                  ),
                  child: const Text('Setup Connection'),
                ),
                const Divider(),
                TextField(
                  controller: commandController,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    hintText: "Enter 1 for Lead 2 & 2 for lead-12",
                  ),
                ),
                ElevatedButton(
                  onPressed: () {
                    //create test
                    createTest();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                  ),
                  child: const Text('Send Command'),
                ),
                Wrap(
                  children: List.generate(ecgList.length, (index) {
                    return Container(
                      margin: const EdgeInsets.all(4),
                      child: ElevatedButton(
                        onPressed: () {
                          //start test position wise
                          startEcgTest(ecgList[index]);
                        },
                        child: Text(ecgList[index]),
                      ),
                    );
                  }).toList(),
                ),
                ElevatedButton(
                  onPressed: () {
                    //generate report
                    generateReport();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.grey,
                  ),
                  child: const Text('Generate Report'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

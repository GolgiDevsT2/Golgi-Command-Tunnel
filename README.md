# GolgiCommandTunnel
A Golgi based VPN-like utility to allow remote invocation of commands on Unix systems. 

### Preparation

* Register for a developer account (it's free) at [devs.golgi.io](https://devs.golgi.io)
* Generate a new Application Key on the developer portal
* Download and install the SDK from the developer portal.
* Clone this repository
* Create a file called ```Golgi.DevKey``` containing a single line (the Developer Key you were assigned)
* Create another file called ```Golgi.AppKey``` containing a single line (the Application Key assigned to your new application).


### Build the Android Application
No Android implementation yet.

### Build the iOS Application
No iOS implementation yet.

### Build the  Java Application
* Change directory to the Server directory
* Download the needed jar files by running: sh getjars.sh
* Build the application by running: make

### Test the Application on the same machine
* Open two terminal windows and change directory to the Server directory
* In one window, run the "server" component: ./gct-svr.sh test-svr
* In the other window, set the endpoint name using an environment variable: export GCT_NAME=test-cli
* Run a simple command on the server: ./gct.sh test-svr pwd
* You should see the full path of the Server directory printed out.
* Ctrl-C out of the server implementation.

### Test the Application with a second machine
* Download and build the application on a second machine as described above
* Run the "server" component on the second machine as described above
* Run a simple command on the first machine: ./gct.sh test-svr hostname
* You should see the hostname of the second machine printed out.




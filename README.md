A simple Redis demo.

This tiny demo illustrates the use of a Redis server and an Android app to retrieve values from a hash key stored on the server side. Conceptually the code consists of a Python-based web app using Flask to offer a number of images on a web page. Clicking one of the images commits its name and bitmap to a predefined hash key. Using Jedis, the Android app pulls the name and the initial bitmap from the server side upon startup and watches through a keyspace event notification on this particular key for any changes. Should the content of the hash key change, the image and name on the Android screen is automatically updated.
The choice of Jedis as the main Java client library was made for the following reasons:
- I wanted something compact which can easily be installed on a mobile device with limited resources in terms of CPU power and memory,
- Standard build environment integration is a must, so availablity in the standard Maven repositories is a prerequisite (including Android Studio!),
- The API should simple and easy to learn, so I favoured Jedis instead of more powerful but complex APIs such as Lettuce.
I created this sample code to provide a shorter learning curve than the most of the examples you can find on the Internet.

Caveat: This code comes as it is. No effort has been made to finish and polish this code base for production deployment (for example, error checking and logging is virtually non-existent). Also commenting could be vastly improved. Proceed at your own risk :-) and enjoy learning and playing with it.

Prerequisites for running this demo which worked for me:

1. Install Redis on bare metal (ie, the OS) or in a container, ensuring to enable the keyspace notification in the server side configuration (notify-keyspace-events should be set to at least "Kh"),

2. Using PIP, install the Flask framework and the Python Redis  library (redis-py) in either a virtualenv or on bare metal (ie. the operating system) on your server. I tested the server side Python with Python 3 only but I don't see why it shouldn't run on Python 2 also (the implementation doesn't use advanced API features, so even the py-redis default package from the  OS vendor's repositories should work – as they did on my Debian Stretch installation). 

3. Install the Python Flask server code at a location of your choice and populate the "static" subdirectory with sample images, As the sample app represents a fashion blog (where a blogger can choose a picture which is then stored in the Redis hash and synchronized to the Android app running on the mobile), I chose images comparable to Ugly Dolls :-) but it goes without saying that these images are not part of the repo due to copyright reasons. Any images roughly square which can be rendered nicely on a web page will do (I chose approximately 500x500 px sizes). The current code base only recognizes ".png" files; this can easily be extended by expanding the "exts" list the function "getImages" in the server app called "app.py". Also reflect any password changes in the storeImageinRedis function of the server code. Depending on your particular Flask deployment method, either modify the app.run invocation or use flask --host=0.0.0.0 ... if you want to go beyond localhost for connection acceptance. 

4. Once you run the Flask app, you should be greeted with a nice webpage on port 5000 (Flask default configuration) offering the images you copied into the "static" folder. Clicking on an image will store the bitmap from the image file alonside the filename minus its extension in the hash. You can check this by logging into the Redis instance via redis-cli and checking the values under the hash key called "fashionPic". 

5. Using your Android / Java IDE of choice from IntelliJ :-) (I used AndroidStudio), open the project in the main repo folder. Verify in the IDE in the project configuration that jedis is listed as a library dependency (otherwise your Android project won't build). 

6. Modify the Redis server IP address in the member function "setupDB" to reflect your Redis server instance, providing an authentication as required by removing the comments in front of the auth member function invocation in setupDB. 
    
Some hints:
    • As usual, logcat or similar mechanisms (IDE-based or not) on the Android side provide you with a view from the client side. 
    
If you want to help out by making the core more robust (error handling, comments, etc. come to mind :-) ), feel free to send me PRs with your improvements! More than happy to incorporate them if they check out.
I hope this code serves you as a starting point to explore the opportunities of using Redis in embedded / mobile environments - have fun exploring it as much as I had writing the code!

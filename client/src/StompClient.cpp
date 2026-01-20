#include <stdlib.h>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
using std::unique_ptr;
using std::string;
using std::cin;
using std::cout;
using std::endl;
using std::stringstream;
using std::vector;
using std::thread;

void SocketReaderThread(ConnectionHandler* connectionHandler, StompProtocol& protocol) {
	while (protocol.isLoggedIn()) {
		string response;
		if (!connectionHandler->getFrameAscii(response, '\0')) {
			cout << "Disconnected from server, exiting..." << endl;
			protocol.setLoggedIn(false);
			break;
		}
		protocol.processMessage(response);
	}
}

int main(int argc, char *argv[]) {
	while (1){
		unique_ptr<ConnectionHandler> handler;
		StompProtocol protocol;

		//=============================LOGIN LOOP START================================
		while (!protocol.isLoggedIn()) {
			// get user input
			const short bufsize = 1024;
			char buf[bufsize];
			cin.getline(buf, bufsize);
			string line(buf);

			// parse user input
			stringstream line_stream(line);
			string command;
			line_stream >> command;

			if (command == "login") {
				//setting up connection handler if first login attempt
				string host_port;
				line_stream >> host_port;
				size_t colon = host_port.find(':');
				std::string host = host_port.substr(0, colon);
				short port = atoi(host_port.substr(colon + 1).c_str());

				handler.reset(new ConnectionHandler(host, port));

				if(!handler->connect()){
					cout << "Could not connect to server" << endl;
					continue;
				}
				// socket connected, send login frame
				vector<string> login_frame_vector = protocol.proccessKeyboardMessage(line);
				string login_frame = login_frame_vector[0];
				handler->sendFrameAscii(login_frame, '\0');
				//proccessing server response
				string response;
				handler->getFrameAscii(response, '\0');
				protocol.processServerMessage(response);

				continue;
			}
			// if command is not login
			cout << "You must log in first" << endl;
		}
		//=============================LOGIN LOOP END=================================

		//login was successful, start socket thread
		std::thread socketThread(SocketReaderThread, handler.get(), std::ref(protocol));	

		//=============================LOGGED IN LOOP START===========================
		while (protocol.isLoggedIn()) {
			const short bufsize = 1024;
			char buf[bufsize];
			cin.getline(buf, bufsize);
			string line(buf);

			if (!protocol.isLoggedIn()) break;

			if (!line.empty()){
				vector<string> frames_vector = protocol.proccessKeyboardMessage(line);
				for (const string& frame : frames_vector) 
					handler->sendFrameAscii(frame, '\0');
			}

			//gracfully handle logout, to not hang waiting for keyboard input 
			if (line.substr(0, 6) == "logout") {
                    if (socketThread.joinable()) socketThread.join();
                    break;
                }
            }
			//=============================LOGGED IN LOOP END===========================
			
		    //in case of sudden exit caused by an error message, clean up
			if (socketThread.joinable()) socketThread.join();
		}	
	}

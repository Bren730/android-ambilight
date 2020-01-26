#ifndef PARSER_H
#define PARSER_H

#include "Arduino.h"

#define CHANNEL_COUNT 4
#define SUBCHANNEL_COUNT 5

// Data variables
#define GRID_SEGMENTS_X  16
#define GRID_SEGMENTS_Y  9
#define AMBILIGHT_DATA_LENGTH GRID_SEGMENTS_X * GRID_SEGMENTS_Y * 3 * 2

// variable positions
#define CMD_FUNCTION     6
#define CMD_FUNCTION_LENGTH 1
#define CMD_DATA_LENGTH  4
#define CMD_DATA_LENGTH_LENGTH 2
#define CMD_DATA         7

// Header values
#define HEADER_LENGTH    4
#define HEADER_0         0x54
#define HEADER_1         0xB5
#define HEADER_2         0xFF
#define HEADER_3         0xFE

// Function values
#define FUNCTION_START   0x00
#define FUNCTION_DATA    0x01
#define FUNCTION_PAUSE   0x02
#define FUNCTION_STOP    0x03

// Other variables
#define AMBILIGHT_PACKET_LENGTH HEADER_LENGTH + CMD_FUNCTION_LENGTH + CMD_DATA_LENGTH_LENGTH + AMBILIGHT_DATA_LENGTH

enum
{
  RECV_HEADER_0,
  RECV_HEADER_1,
  RECV_HEADER_2,
  RECV_HEADER_3,
  RECV_DATA_LENGTH_0,
  RECV_DATA_LENGTH_1,
  RECV_FUNCTION,
  RECV_DATA
};

class Parser {

	public:
		Parser();
		uint8_t receiveBuffer[AMBILIGHT_PACKET_LENGTH];
		uint16_t colorData[GRID_SEGMENTS_X][GRID_SEGMENTS_Y][3];
		uint16_t outputData[CHANNEL_COUNT][SUBCHANNEL_COUNT];

		void parseByte(uint8_t b);
		void processAmbilightData(uint8_t _colorData[], uint16_t length);
		void addEventListener(void (*listener) ());
		void onAmbilightPacketReceived(void (*listener) ());

	private:
		uint8_t next_byte = RECV_HEADER_0;
		uint16_t received_length = 0;
		uint16_t data_length = 0;
		uint16_t received_data_length = 0;

		// Event listeners
		void (*eventListener) ();
		void (*onAmbilightPacketReceivedListener) ();
};

#endif
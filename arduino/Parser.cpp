#include "Parser.h"
#include "Utils.h"

Parser::Parser()
{
	
}

void Parser::onAmbilightPacketReceived(void (*listener) ())
{
  onAmbilightPacketReceivedListener = listener;
}

void Parser::onStartPacketReceived(void (*listener) ())
{
  onStartPacketReceivedListener = listener;
}

void Parser::onPausePacketReceived(void (*listener) ())
{
  onPausePacketReceivedListener = listener;
}

void Parser::onStopPacketReceived(void (*listener) ())
{
  onStopPacketReceivedListener = listener;
}

void Parser::parseByte(uint8_t b)
{

  switch (next_byte)
  {
    case RECV_HEADER_0:
      if (b == HEADER_0)
      {
        received_length = 0;
        receiveBuffer[received_length] = b;
        received_length++;
        next_byte = RECV_HEADER_1;

        //      setLED(0, 0, 0, 65535);
        //      tlc.write();
        //      Serial.println("Found header byte 0");
      }
      else
      {
        next_byte = RECV_HEADER_0;
        //      Serial.printf("Lost header byte(0x%02X)\n", b);
      }
      break;

    case RECV_HEADER_1:
      if (b == HEADER_1)
      {
        receiveBuffer[received_length] = b;
        received_length++;
        next_byte = RECV_HEADER_2;

        //      setLED(1, 0, 0, 65535);
        //      tlc.write();

        //      Serial.println("Found header byte 1");
      }
      else
      {
        next_byte = RECV_HEADER_0;
        //      Serial.printf("Lost header byte(0x%02X)\n", b);
      }
      break;

    case RECV_HEADER_2:
      if (b == HEADER_2)
      {
        receiveBuffer[received_length] = b;
        received_length++;
        next_byte = RECV_HEADER_3;

        //      setLED(2, 0, 0, 65535);
        //      tlc.write();

        //      Serial.println("Found header byte 2");
      }
      else
      {
        next_byte = RECV_HEADER_0;
        //      Serial.printf("Lost header byte(0x%02X)\n", b);
      }
      break;

    case RECV_HEADER_3:
      if (b == HEADER_3)
      {
        receiveBuffer[received_length] = b;
        received_length++;
        next_byte = RECV_DATA_LENGTH_0;

        //      setLED(3, 0, 0, 65535);
        //      tlc.write();

        //      Serial.println("Header complete");
        //      Serial.println();
      }
      else
      {
        next_byte = RECV_HEADER_0;
        //      Serial.printf("Lost header byte(0x%02X)\n", b);
      }
      break;

    case RECV_DATA_LENGTH_0:
      receiveBuffer[received_length] = b;
      received_length++;
      next_byte = RECV_DATA_LENGTH_1;
      break;

    case RECV_DATA_LENGTH_1:
      receiveBuffer[received_length] = b;
      received_length++;

      data_length = uint8Touint16(receiveBuffer[CMD_DATA_LENGTH], receiveBuffer[CMD_DATA_LENGTH + 1]);

      next_byte = RECV_FUNCTION;

      // Serial.print("Data length: ");
      // Serial.println(data_length);
      break;

    case RECV_FUNCTION:

      receiveBuffer[received_length] = b;
      received_length++;

      // setLED(0, 0, 65535, 0);
      // setLED(1, 0, 65535, 0);
      // setLED(2, 0, 65535, 0);
      // setLED(3, 0, 65535, 0);
      // tlc.write();

      if (receiveBuffer[CMD_FUNCTION] == FUNCTION_START)
      {
        next_byte = RECV_HEADER_0;
        received_length = 0;
        received_data_length = 0;

        onStartPacketReceivedListener();
      }
      else if (receiveBuffer[CMD_FUNCTION] == FUNCTION_STOP)
      {
        next_byte = RECV_HEADER_0;
        received_length = 0;
        received_data_length = 0;
        
        onStopPacketReceivedListener();
      }
      else
      {
        next_byte = RECV_DATA;
      }
      
      break;



    case RECV_DATA:
      receiveBuffer[received_length] = b;
      received_length++;
      received_data_length++;

      if (received_data_length == data_length)
      {
        // Serial.printf("Received %d of %d bytes\n", received_data_length, data_length);

        switch (receiveBuffer[CMD_FUNCTION])
        {
          case FUNCTION_START:
            // Serial.println("FUNCTION_START");
            //                 isAmbilightOn = true;
            onStartPacketReceivedListener();
            break;

          case FUNCTION_DATA:
            // Serial.println("FUNCTION_AMBILIGHT");
            processAmbilightData(receiveBuffer, sizeof(receiveBuffer));
            onAmbilightPacketReceivedListener();

            break;

          case FUNCTION_PAUSE:
            onPausePacketReceivedListener();
            break;

          case FUNCTION_STOP:
            onStopPacketReceivedListener();
            break;
        }

        next_byte = RECV_HEADER_0;
        received_length = 0;
        received_data_length = 0;

        Serial.write(0xFF);
      }
      break;
  }
}

void Parser::processAmbilightData(uint8_t _colorData[], uint16_t length)
{

  if (length == AMBILIGHT_PACKET_LENGTH)
  {
    for (uint16_t y = 0; y < GRID_SEGMENTS_Y; y++)
    {
      for (uint16_t x = 0; x < GRID_SEGMENTS_X; x++)
      {
        uint16_t arrayPosition = ((GRID_SEGMENTS_X * y) + x) * 3 * 2 + CMD_DATA;

        // Serial.printf("x: %d, y: %d pos: %d, value: %d \n", x, y, arrayPosition, _colorData[arrayPosition]);

        colorData[x][y][0] = uint8Touint16(_colorData[arrayPosition + 0], _colorData[arrayPosition + 1]);
        colorData[x][y][1] = uint8Touint16(_colorData[arrayPosition + 2], _colorData[arrayPosition + 3]);
        colorData[x][y][2] = uint8Touint16(_colorData[arrayPosition + 4], _colorData[arrayPosition + 5]);
      }
    }

    // Serial.printf("parsedRed %d\n", colorData[0][0][0]);
    // Serial.printf("parsedGreen %d\n", colorData[0][0][1]);
    // Serial.printf("parsedBlue %d\n", colorData[0][0][2]);

    // printColorArray();
  }
  else
  {
    // Serial.printf("Ambilight data length incorrect, received %d, expected %d\n", length, AMBILIGHT_DATA_LENGTH);
  }
}























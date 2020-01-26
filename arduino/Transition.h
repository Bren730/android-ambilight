#ifndef Transition_h
#define Transition_h

#include "Arduino.h"

class Transition
{
  public:
    Transition();
    Transition(uint16_t _transitionTime);

    // functions
    void start();
    void reset();
    void stop();
    
    void setTransitionTime(uint16_t _transitionTime);

    float getProgress();
    float getInverseProgress();
    uint16_t getTransitionTime();
    unsigned long getStartTime();
    uint16_t getElapsedTime();
    bool isRunning();

  private:
    unsigned long startTime = 0;
    uint16_t transitionTime = 400;
    float progress = 0;
    bool running = false;
    
};

#endif 

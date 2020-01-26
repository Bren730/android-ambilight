#include "Arduino.h"
#include "Transition.h"

Transition::Transition()
{

}

Transition::Transition(uint16_t _transitionTime)
{
	transitionTime = _transitionTime;
}

void Transition::start()
{
	startTime = millis();
	progress = 0;
	running = true;
}

void Transition::reset()
{

}

void Transition::stop()
{
	startTime = 0;
	progress = 0;
	running = false;
}

void Transition::setTransitionTime(uint16_t _transitionTime)
{
	transitionTime = _transitionTime;
}

float Transition::getProgress()
{
	if (!running)
	{
		return 0;
	}
	
	if (millis() - startTime <= transitionTime)
	{
		return ((float)(millis() - startTime) / transitionTime);
	}
	else
	{
		return 1.0;
	}

	
}
float Transition::getInverseProgress()
{
	return 1 - getProgress();
}

uint16_t Transition::getTransitionTime()
{
	return transitionTime;
}

unsigned long Transition::getStartTime()
{
	return startTime;
}

uint16_t Transition::getElapsedTime()
{
	return millis() - startTime;
}

bool Transition::isRunning()
{
	return running;
}
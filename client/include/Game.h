#pragma once
#include <stdlib.h>
#include <algorithm> //for sorting
#include "../include/event.h"
using std::string;
using std::vector;
using std::map;

class Game {
private:
    string team_a_name;
    string team_b_name;

    vector<Event> events;

    map<std::string, std::string> general_stats;
    map<std::string, std::string> team_a_stats;
    map<std::string, std::string> team_b_stats;


public:
    Game(std::string team_a, std::string team_b);

    void addEvent(const Event& event);

    vector<Event> getAllSortedEvents() const;

    const map<string, string>& getGeneralStats() const;
    const map<string, string>& getTeamAStats() const;
    const map<string, string>& getTeamBStats() const;
};
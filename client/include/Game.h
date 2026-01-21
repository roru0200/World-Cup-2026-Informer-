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

    Game() : team_a_name(""), team_b_name(""), events(), general_stats(), team_a_stats(), team_b_stats() {}
    Game(string team_a, string team_b);
    
    void addEvent(const Event& event);

    vector<Event> getEvents() const;

    const map<string, string>& getGeneralStats() const;
    const map<string, string>& getTeamAStats() const;
    const map<string, string>& getTeamBStats() const;
};
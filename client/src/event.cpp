#include "../include/event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription, bool beforeHalftime)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription), beforeHalftime(beforeHalftime)
{
}

Event::~Event()
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

bool Event::get_beforeHalftime() const
{
    return this->beforeHalftime;
}
void Event::set_beforeHalftime(bool beforeHalftime)
{
    this->beforeHalftime = beforeHalftime;
}

std::string Event::trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\r\n");
    if (std::string::npos == first) return "";
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, (last - first + 1));
}

bool Event::isIndented(const std::string& line) {
    if (line.empty()) return false;
    return (line[0] == ' ' || line[0] == '\t');
}

Event::Event(const std::string &frame_body) : team_a_name(""), team_b_name(""), name(""), time(0), game_updates(), team_a_updates(), team_b_updates(), description(""), beforeHalftime(true)
{
    std::stringstream ss(frame_body);
    std::string line;
    
    ParseState currentState = NONE;

    while (std::getline(ss, line)) {
        bool indented = isIndented(line);
        std::string trimmedLine = trim(line);
        
        if (trimmedLine.empty()) continue;

        // Description Body Handling
        if (currentState == DESCRIPTION && trimmedLine.find(':') == std::string::npos) {
            if (!description.empty()) description += "\n";
            description += trimmedLine;
            continue;
        }

        // Key-Value Parsing
        size_t delimiterPos = trimmedLine.find(':');
        if (delimiterPos != std::string::npos) {
            std::string key = trim(trimmedLine.substr(0, delimiterPos));
            std::string value = trim(trimmedLine.substr(delimiterPos + 1));

            // Section Headers
            if (key == "general game updates") {
                currentState = GAME_UPDATES;
                continue; 
            } else if (key == "team a updates") {
                currentState = TEAM_A_UPDATES;
                continue;
            } else if (key == "team b updates") {
                currentState = TEAM_B_UPDATES;
                continue;
            } else if (key == "description") {
                currentState = DESCRIPTION;
                if (!value.empty()) description = value;
                continue;
            }

            // Indented Data
            if (indented) {
                if (currentState == GAME_UPDATES) game_updates[key] = value;
                else if (currentState == TEAM_A_UPDATES) team_a_updates[key] = value;
                else if (currentState == TEAM_B_UPDATES) team_b_updates[key] = value;
            }
            // Top-Level Data
            else {
                currentState = NONE; 
                if (key == "team a") team_a_name = value;
                else if (key == "team b") team_b_name = value;
                else if (key == "event name") name = value;
                else if (key == "time") {
                    try { time = std::stoi(value); } catch(...) { time = 0; }
                }
            }
        }
        else if (currentState == DESCRIPTION) {
             if (!description.empty()) description += "\n";
             description += trimmedLine;
        }
    }
}

names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description, true));//true is a placeholder for beforeHalftime
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}
# Copyright 2014 TWO SIGMA OPEN SOURCE, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# packages needed to run beaker

# java
sudo add-apt-repository --yes ppa:webupd8team/java
sudo apt-get update
sudo apt-get install -y oracle-java8-installer # javac -v = 1.8.XXX

# nginx
sudo add-apt-repository --yes ppa:nginx/stable
sudo apt-get update
sudo apt-get install -y nginx


NOTE: TwoWayView's API is not final yet and code is under heavy development
at the moment. Do not rely on it for production code just yet. Feedback and
patches are very welcome!

What is it?
===========

An AdapterView with support for vertical and horizontal scrolling.

Features
========

* View recycling while scrolling, just like ListView/GridView.
* Vertical and horizontal scrolling support.

A lot more to come, stay tuned.

How do I use it?
================

1. Import TwoWayView as a library to your project.

2. Add a `TwoWayView` to your layout.

3. Set an Adapter to the TwoWayView.

The sample app uses all features available in the widget.

Want to help?
=============

File new issues to discuss specific aspects of the API and to propose new
features. Here's a few (definitely not all!) of the upcoming features:

* Accessibility support
* Keyboard events and navigation
* Focus handling

Build instructions
==================

If you use Eclipse, simply import the Android project from existing source.
If you use Maven, you'll need to deploy the latest Support Library locally
using the [maven-android-sdk-deployer](https://github.com/mosabua/maven-android-sdk-deployer).

License
=======

    Copyright (C) 2013 Lucas Rocha

    TwoWayView's code is based on bits and pieces of Android's
    AbsListView, Listview, and StaggeredGridView.

    Copyright (C) 2012 The Android Open Source Project

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

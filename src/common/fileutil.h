//==========================================================================
//  FILEUTIL.H - part of
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//  Author: Andras Varga
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __FILEUTIL_H
#define __FILEUTIL_H

#include <string>
#include "commondefs.h"

NAMESPACE_BEGIN

#ifdef _WIN32
#define PATH_SEPARATOR   ";"
#else
#define PATH_SEPARATOR   ":;"
#endif

COMMON_API std::string fileNameToSlash(const char *fileName);
COMMON_API void splitFileName(const char *pathname, std::string& dir, std::string& fnameonly);
COMMON_API std::string directoryOf(const char *pathname);
COMMON_API std::string tidyFilename(const char *pathname, bool slashes=false);
COMMON_API std::string toAbsolutePath(const char *pathname);
COMMON_API std::string concatDirAndFile(const char *basedir, const char *pathname);
COMMON_API bool fileExists(const char *pathname); // true if file/directory exists
COMMON_API bool isDirectory(const char *pathname);
COMMON_API void removeFile(const char *fname, const char *descr);
COMMON_API void mkPath(const char *pathname);

/**
 * Utility class for temporary change of directory. Changes back to
 * original dir when goes out of scope. Does nothing if NULL is passed
 * to the constructor.
 */
class COMMON_API PushDir
{
  private:
    std::string olddir;
  public:
    PushDir(const char *changetodir);
    ~PushDir();
};

NAMESPACE_END


#endif

